package audio

import (
	"bytes"
	"math"
	"testing"
)

// generateTone16 generates 16-bit mono PCM sine wave samples.
func generateTone16(durationSec float64, sampleRate int, freqHz float64, amplitude float64) []byte {
	totalSamples := int(durationSec * float64(sampleRate))
	buf := new(bytes.Buffer)
	for i := 0; i < totalSamples; i++ {
		t := float64(i) / float64(sampleRate)
		sampleVal := int16(amplitude * math.Sin(2.0*math.Pi*freqHz*t))
		buf.WriteByte(byte(sampleVal & 0xFF))
		buf.WriteByte(byte((sampleVal >> 8) & 0xFF))
	}
	return buf.Bytes()
}

// generateSilence16 generates 16-bit mono zeroed PCM samples.
func generateSilence16(durationSec float64, sampleRate int) []byte {
	totalBytes := int(durationSec * float64(sampleRate) * 2)
	return make([]byte, totalBytes)
}

func TestStreamChunker_SilenceSplit(t *testing.T) {
	cfg := ChunkerConfig{
		SampleRate:            16000,
		Channels:              1,
		BitsPerSample:         16,
		SilenceThresholdRMS:   350.0,
		MinSilenceDurationSec: 0.4,
		MinChunkDurationSec:   5.0,
		MaxChunkDurationSec:   25.0,
	}
	chunker := NewStreamChunker(cfg)

	// Step 1: Push 4 seconds of tone (amplitude 5000) - below min chunk duration
	tone4s := generateTone16(4.0, 16000, 440, 5000)
	chunk := chunker.Push(tone4s)
	if chunk != nil {
		t.Fatalf("expected nil chunk at 4s, got chunk")
	}

	// Step 2: Push 2 more seconds of tone (total tone = 6s >= min 5s)
	tone2s := generateTone16(2.0, 16000, 440, 5000)
	chunk = chunker.Push(tone2s)
	if chunk != nil {
		t.Fatalf("expected nil chunk during active sound, got chunk")
	}

	// Step 3: Push 0.5s silence (exceeds min silence 0.4s) -> Should trigger split!
	silenceHalfSec := generateSilence16(0.5, 16000)
	chunk = chunker.Push(silenceHalfSec)
	if chunk == nil {
		t.Fatalf("expected chunk split upon silence detection, got nil")
	}

	if chunk.Index != 0 {
		t.Errorf("expected chunk.Index 0, got %d", chunk.Index)
	}
	if chunk.StartTimeSec != 0.0 {
		t.Errorf("expected StartTimeSec 0.0, got %f", chunk.StartTimeSec)
	}
	if math.Abs(chunk.EndTimeSec-6.5) > 0.01 {
		t.Errorf("expected EndTimeSec ~6.5, got %f", chunk.EndTimeSec)
	}
}

func TestStreamChunker_HardLimitSplit(t *testing.T) {
	cfg := ChunkerConfig{
		SampleRate:            16000,
		Channels:              1,
		BitsPerSample:         16,
		SilenceThresholdRMS:   350.0,
		MinSilenceDurationSec: 0.4,
		MinChunkDurationSec:   5.0,
		MaxChunkDurationSec:   20.0, // Hard ceiling 20s
	}
	chunker := NewStreamChunker(cfg)

	// Continuous loud tone for 21 seconds without silence
	tone21s := generateTone16(21.0, 16000, 440, 5000)
	chunk := chunker.Push(tone21s)

	if chunk == nil {
		t.Fatalf("expected hard ceiling chunk split, got nil")
	}
	if chunk.Index != 0 {
		t.Errorf("expected index 0, got %d", chunk.Index)
	}
	if math.Abs(chunk.EndTimeSec-21.0) > 0.01 {
		t.Errorf("expected EndTimeSec ~21.0, got %f", chunk.EndTimeSec)
	}
}

func TestStreamChunker_Flush(t *testing.T) {
	cfg := DefaultChunkerConfig()
	chunker := NewStreamChunker(cfg)

	// Push 3s of audio (not enough for auto-flush)
	tone3s := generateTone16(3.0, 16000, 440, 3000)
	chunk := chunker.Push(tone3s)
	if chunk != nil {
		t.Fatalf("expected nil chunk")
	}

	// Flush manually at session end
	chunk = chunker.Flush()
	if chunk == nil {
		t.Fatalf("expected flushed chunk, got nil")
	}
	if chunk.Index != 0 {
		t.Errorf("expected index 0, got %d", chunk.Index)
	}
	if math.Abs(chunk.EndTimeSec-3.0) > 0.01 {
		t.Errorf("expected EndTimeSec ~3.0, got %f", chunk.EndTimeSec)
	}

	// Subsequent flush on empty should return nil
	if secondFlush := chunker.Flush(); secondFlush != nil {
		t.Errorf("expected nil on empty flush, got chunk")
	}
}
