package audio

import (
	"bytes"
	"encoding/binary"
	"testing"
)

func TestWrapPCMToWAV_Valid(t *testing.T) {
	sampleRate := 16000
	channels := 1
	bitsPerSample := 16
	pcmData := []byte{0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07}

	wavBytes, err := WrapPCMToWAV(pcmData, sampleRate, channels, bitsPerSample)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	expectedHeaderSize := 44
	if len(wavBytes) != expectedHeaderSize+len(pcmData) {
		t.Fatalf("expected total length %d, got %d", expectedHeaderSize+len(pcmData), len(wavBytes))
	}

	// Verify RIFF
	if string(wavBytes[0:4]) != "RIFF" {
		t.Errorf("expected RIFF, got %s", string(wavBytes[0:4]))
	}

	// Verify ChunkSize (36 + len(pcmData))
	var chunkSize uint32
	binary.Read(bytes.NewReader(wavBytes[4:8]), binary.LittleEndian, &chunkSize)
	if chunkSize != uint32(36+len(pcmData)) {
		t.Errorf("expected chunkSize %d, got %d", 36+len(pcmData), chunkSize)
	}

	// Verify WAVE
	if string(wavBytes[8:12]) != "WAVE" {
		t.Errorf("expected WAVE, got %s", string(wavBytes[8:12]))
	}

	// Verify fmt
	if string(wavBytes[12:16]) != "fmt " {
		t.Errorf("expected 'fmt ', got %s", string(wavBytes[12:16]))
	}

	// Verify AudioFormat == 1 (PCM)
	var audioFormat uint16
	binary.Read(bytes.NewReader(wavBytes[20:22]), binary.LittleEndian, &audioFormat)
	if audioFormat != 1 {
		t.Errorf("expected audioFormat 1, got %d", audioFormat)
	}

	// Verify Channels
	var ch uint16
	binary.Read(bytes.NewReader(wavBytes[22:24]), binary.LittleEndian, &ch)
	if ch != uint16(channels) {
		t.Errorf("expected channels %d, got %d", channels, ch)
	}

	// Verify SampleRate
	var sr uint32
	binary.Read(bytes.NewReader(wavBytes[24:28]), binary.LittleEndian, &sr)
	if sr != uint32(sampleRate) {
		t.Errorf("expected sampleRate %d, got %d", sampleRate, sr)
	}

	// Verify Data subchunk
	if string(wavBytes[36:40]) != "data" {
		t.Errorf("expected 'data', got %s", string(wavBytes[36:40]))
	}

	// Verify DataSize
	var dataSize uint32
	binary.Read(bytes.NewReader(wavBytes[40:44]), binary.LittleEndian, &dataSize)
	if dataSize != uint32(len(pcmData)) {
		t.Errorf("expected dataSize %d, got %d", len(pcmData), dataSize)
	}

	// Verify payload matches
	if !bytes.Equal(wavBytes[44:], pcmData) {
		t.Errorf("payload mismatch")
	}
}

func TestWrapPCMToWAV_InvalidParams(t *testing.T) {
	pcm := []byte{0x00, 0x01}

	if _, err := WrapPCMToWAV(pcm, 0, 1, 16); err == nil {
		t.Errorf("expected error for sample rate 0")
	}
	if _, err := WrapPCMToWAV(pcm, 16000, 0, 16); err == nil {
		t.Errorf("expected error for channels 0")
	}
	if _, err := WrapPCMToWAV(pcm, 16000, 1, 12); err == nil {
		t.Errorf("expected error for invalid bitsPerSample 12")
	}
}
