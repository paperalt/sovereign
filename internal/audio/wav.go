package audio

import (
	"bytes"
	"encoding/binary"
	"fmt"
)

// WrapPCMToWAV encapsulates raw PCM audio bytes into a standard RIFF/WAVE container.
func WrapPCMToWAV(pcmData []byte, sampleRate int, numChannels int, bitsPerSample int) ([]byte, error) {
	if sampleRate <= 0 {
		return nil, fmt.Errorf("invalid sample rate: %d", sampleRate)
	}
	if numChannels <= 0 {
		return nil, fmt.Errorf("invalid channel count: %d", numChannels)
	}
	if bitsPerSample != 8 && bitsPerSample != 16 && bitsPerSample != 24 && bitsPerSample != 32 {
		return nil, fmt.Errorf("unsupported bits per sample: %d", bitsPerSample)
	}

	buf := new(bytes.Buffer)
	byteRate := uint32(sampleRate * numChannels * (bitsPerSample / 8))
	blockAlign := uint16(numChannels * (bitsPerSample / 8))
	// Ensure pcmData contains only complete sample frames (truncate incomplete trailing bytes)
	if remainder := len(pcmData) % int(blockAlign); remainder != 0 {
		pcmData = pcmData[:len(pcmData)-remainder]
	}
	dataSize := uint32(len(pcmData))
	chunkSize := uint32(36 + dataSize)

	// RIFF Chunk Descriptor
	buf.WriteString("RIFF")
	if err := binary.Write(buf, binary.LittleEndian, chunkSize); err != nil {
		return nil, err
	}
	buf.WriteString("WAVE")

	// "fmt " Sub-chunk
	buf.WriteString("fmt ")
	if err := binary.Write(buf, binary.LittleEndian, uint32(16)); err != nil { // Subchunk1Size for PCM = 16
		return nil, err
	}
	if err := binary.Write(buf, binary.LittleEndian, uint16(1)); err != nil { // AudioFormat: 1 = PCM
		return nil, err
	}
	if err := binary.Write(buf, binary.LittleEndian, uint16(numChannels)); err != nil {
		return nil, err
	}
	if err := binary.Write(buf, binary.LittleEndian, uint32(sampleRate)); err != nil {
		return nil, err
	}
	if err := binary.Write(buf, binary.LittleEndian, byteRate); err != nil {
		return nil, err
	}
	if err := binary.Write(buf, binary.LittleEndian, blockAlign); err != nil {
		return nil, err
	}
	if err := binary.Write(buf, binary.LittleEndian, uint16(bitsPerSample)); err != nil {
		return nil, err
	}

	// "data" Sub-chunk
	buf.WriteString("data")
	if err := binary.Write(buf, binary.LittleEndian, dataSize); err != nil {
		return nil, err
	}
	if _, err := buf.Write(pcmData); err != nil {
		return nil, err
	}

	return buf.Bytes(), nil
}
