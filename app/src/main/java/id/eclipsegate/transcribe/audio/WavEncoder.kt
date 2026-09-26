package id.eclipsegate.transcribe.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavEncoder {

    /**
     * Encodes raw 16-bit Mono Linear PCM bytes into an RFC 2361 compliant RIFF/WAVE container.
     */
    fun encodePcmToWav(
        pcmData: ByteArray,
        sampleRate: Int = 16000,
        channels: Short = 1,
        bitsPerSample: Short = 16
    ): ByteArray {
        val byteRate = sampleRate * channels * (bitsPerSample / 8)
        val blockAlign = (channels * (bitsPerSample / 8)).toShort()
        val totalDataLen = pcmData.size
        val totalAudioLen = totalDataLen + 36

        val buffer = ByteBuffer.allocate(44 + totalDataLen).apply {
            order(ByteOrder.LITTLE_ENDIAN)

            // RIFF header
            put('R'.code.toByte())
            put('I'.code.toByte())
            put('F'.code.toByte())
            put('F'.code.toByte())
            putInt(totalAudioLen)
            put('W'.code.toByte())
            put('A'.code.toByte())
            put('V'.code.toByte())
            put('E'.code.toByte())

            // Format subchunk ("fmt ")
            put('f'.code.toByte())
            put('m'.code.toByte())
            put('t'.code.toByte())
            put(' '.code.toByte())
            putInt(16) // Subchunk1Size for PCM
            putShort(1) // AudioFormat (1 = Linear PCM)
            putShort(channels)
            putInt(sampleRate)
            putInt(byteRate)
            putShort(blockAlign)
            putShort(bitsPerSample)

            // Data subchunk ("data")
            put('d'.code.toByte())
            put('a'.code.toByte())
            put('t'.code.toByte())
            put('a'.code.toByte())
            putInt(totalDataLen)

            // Raw PCM Samples
            put(pcmData)
        }

        return buffer.array()
    }
}
