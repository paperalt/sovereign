package audio

import (
	"bytes"
	"encoding/binary"
	"math"
	"sync"
	"time"
)

// AudioChunk represents a segmented slice of audio ready for transcription.
type AudioChunk struct {
	Index        int
	Data         []byte // Raw PCM bytes
	StartTimeSec float64
	EndTimeSec   float64
}

// ChunkerConfig specifies parameters for silence-based audio chunking.
type ChunkerConfig struct {
	SampleRate            int     // Default: 16000
	Channels              int     // Default: 1
	BitsPerSample         int     // Default: 16
	SilenceThresholdRMS   float64 // RMS threshold below which audio is considered silence (Default: 350.0)
	MinSilenceDurationSec float64 // Minimum silence duration to trigger a split (Default: 0.4s)
	MinChunkDurationSec   float64 // Minimum audio length before allowing a silence split (Default: 5.0s)
	MaxChunkDurationSec   float64 // Hard ceiling duration to force a split (Default: 25.0s)
}

// DefaultChunkerConfig returns production-ready defaults for 16kHz mono audio.
func DefaultChunkerConfig() ChunkerConfig {
	return ChunkerConfig{
		SampleRate:            16000,
		Channels:              1,
		BitsPerSample:         16,
		SilenceThresholdRMS:   350.0,
		MinSilenceDurationSec: 0.4,
		MinChunkDurationSec:   5.0,
		MaxChunkDurationSec:   25.0,
	}
}

// BetaAdaptiveChunkerConfig returns configuration for the Beta Adaptive streaming pipeline:
// - Lower minimum chunk duration (2.5s instead of 5.0s) for responsive fast transcription.
// - Sensitive silence detection (0.3s) for tight phrase splitting.
// - Maximum chunk duration capped at 12.0s for reduced interim latency.
func BetaAdaptiveChunkerConfig() ChunkerConfig {
	return ChunkerConfig{
		SampleRate:            16000,
		Channels:              1,
		BitsPerSample:         16,
		SilenceThresholdRMS:   280.0,
		MinSilenceDurationSec: 0.3,
		MinChunkDurationSec:   2.5,
		MaxChunkDurationSec:   12.0,
	}
}

// StreamChunker buffers incoming PCM chunks and splits them intelligently.
type StreamChunker struct {
	mu           sync.Mutex
	config       ChunkerConfig
	pcmBuffer    *bytes.Buffer
	chunkIndex   int
	silenceSec   float64
	totalTimeSec float64
	lastFlush    time.Time
}

// NewStreamChunker creates a new StreamChunker instance.
func NewStreamChunker(config ChunkerConfig) *StreamChunker {
	if config.SampleRate <= 0 {
		config.SampleRate = 16000
	}
	if config.Channels <= 0 {
		config.Channels = 1
	}
	if config.BitsPerSample <= 0 {
		config.BitsPerSample = 16
	}
	if config.SilenceThresholdRMS <= 0 {
		config.SilenceThresholdRMS = 350.0
	}
	if config.MinSilenceDurationSec <= 0 {
		config.MinSilenceDurationSec = 0.4
	}
	if config.MinChunkDurationSec <= 0 {
		config.MinChunkDurationSec = 5.0
	}
	if config.MaxChunkDurationSec <= 0 {
		config.MaxChunkDurationSec = 25.0
	}

	return &StreamChunker{
		config:     config,
		pcmBuffer:  new(bytes.Buffer),
		lastFlush:  time.Now(),
		chunkIndex: 0,
	}
}

// Push adds PCM audio data to the buffer and returns an AudioChunk if a boundary condition is met.
func (c *StreamChunker) Push(pcmData []byte) *AudioChunk {
	c.mu.Lock()
	defer c.mu.Unlock()

	if len(pcmData) == 0 {
		return nil
	}

	c.pcmBuffer.Write(pcmData)

	bytesPerSample := c.config.BitsPerSample / 8
	bytesPerSec := c.config.SampleRate * c.config.Channels * bytesPerSample
	frameDurationSec := float64(len(pcmData)) / float64(bytesPerSec)

	// Calculate Root Mean Square (RMS) for 16-bit PCM
	rms := calculateRMS16(pcmData)

	if rms < c.config.SilenceThresholdRMS {
		c.silenceSec += frameDurationSec
	} else {
		c.silenceSec = 0
	}

	currentBufferDuration := float64(c.pcmBuffer.Len()) / float64(bytesPerSec)

	// Boundary condition 1: Silence detected AND minimum chunk duration met
	silenceSplit := c.silenceSec >= c.config.MinSilenceDurationSec && currentBufferDuration >= c.config.MinChunkDurationSec

	// Boundary condition 2: Maximum chunk duration reached (hard ceiling)
	hardLimitSplit := currentBufferDuration >= c.config.MaxChunkDurationSec

	if silenceSplit || hardLimitSplit {
		chunk := &AudioChunk{
			Index:        c.chunkIndex,
			Data:         c.pcmBuffer.Bytes(),
			StartTimeSec: c.totalTimeSec,
			EndTimeSec:   c.totalTimeSec + currentBufferDuration,
		}

		c.chunkIndex++
		c.totalTimeSec += currentBufferDuration
		c.pcmBuffer = new(bytes.Buffer)
		c.silenceSec = 0
		c.lastFlush = time.Now()

		return chunk
	}

	return nil
}

// Flush forces any remaining audio in the buffer to be emitted as a chunk.
func (c *StreamChunker) Flush() *AudioChunk {
	c.mu.Lock()
	defer c.mu.Unlock()

	if c.pcmBuffer.Len() == 0 {
		return nil
	}

	bytesPerSample := c.config.BitsPerSample / 8
	bytesPerSec := c.config.SampleRate * c.config.Channels * bytesPerSample
	currentBufferDuration := float64(c.pcmBuffer.Len()) / float64(bytesPerSec)

	// Discard sub-second trailing fragments or silence to prevent hallucination
	if currentBufferDuration < 0.8 || calculateRMS16(c.pcmBuffer.Bytes()) < c.config.SilenceThresholdRMS {
		c.pcmBuffer = new(bytes.Buffer)
		c.silenceSec = 0
		return nil
	}

	chunk := &AudioChunk{
		Index:        c.chunkIndex,
		Data:         c.pcmBuffer.Bytes(),
		StartTimeSec: c.totalTimeSec,
		EndTimeSec:   c.totalTimeSec + currentBufferDuration,
	}

	c.chunkIndex++
	c.totalTimeSec += currentBufferDuration
	c.pcmBuffer = new(bytes.Buffer)
	c.silenceSec = 0
	c.lastFlush = time.Now()

	return chunk
}

// calculateRMS16 computes RMS for 16-bit signed little-endian PCM samples.
func calculateRMS16(pcm []byte) float64 {
	samples := len(pcm) / 2
	if samples == 0 {
		return 0
	}

	var sum float64
	for i := 0; i < len(pcm)-1; i += 2 {
		val := int16(binary.LittleEndian.Uint16(pcm[i:]))
		sum += float64(val) * float64(val)
	}

	return math.Sqrt(sum / float64(samples))
}
