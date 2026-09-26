package org.sovereign.app.audio

import java.io.ByteArrayOutputStream
import kotlin.math.sqrt

data class AudioChunk(
    val pcmData: ByteArray,
    val startTimeSec: Double,
    val endTimeSec: Double,
    val isFinal: Boolean
)

class AudioStreamChunker(
    private val isAdaptive: Boolean = true,
    private val sampleRate: Int = 16000
) {
    // Audio constants: 16kHz, 16-bit mono -> 32,000 bytes per second
    private val bytesPerSecond = sampleRate * 2

    private val minChunkBytes: Int = if (isAdaptive) (bytesPerSecond * 2.5).toInt() else (bytesPerSecond * 5.0).toInt()
    private val maxChunkBytes: Int = if (isAdaptive) (bytesPerSecond * 12.0).toInt() else (bytesPerSecond * 25.0).toInt()

    private val silenceRmsThreshold = 280.0
    private val deadAirRmsThreshold = 50.0

    private val pcmBuffer = ByteArrayOutputStream(maxChunkBytes)
    private var totalProcessedBytes = 0L
    private var chunkStartByteOffset = 0L

    private var consecutiveSilenceFrames = 0
    private val requiredSilenceFrames = if (isAdaptive) 3 else 5

    /**
     * Ingests a raw PCM frame (typically 128ms or 100ms) and checks if a chunk boundary is reached.
     * Returns an AudioChunk if a boundary condition (silence split or hard limit) is met, or null.
     */
    @Synchronized
    fun processFrame(frame: ByteArray): Pair<AudioChunk?, Double> {
        val rms = calculateRms(frame)

        // Dead-air filtering: if buffer is empty and incoming frame is dead air, suppress it to save memory
        if (pcmBuffer.size() == 0 && rms < deadAirRmsThreshold) {
            totalProcessedBytes += frame.size
            return Pair(null, rms)
        }

        if (pcmBuffer.size() == 0) {
            chunkStartByteOffset = totalProcessedBytes
        }

        pcmBuffer.write(frame)
        totalProcessedBytes += frame.size

        if (rms < silenceRmsThreshold) {
            consecutiveSilenceFrames++
        } else {
            consecutiveSilenceFrames = 0
        }

        val currentBufferSize = pcmBuffer.size()

        // Condition 1: Hard duration limit exceeded
        val reachedHardLimit = currentBufferSize >= maxChunkBytes

        // Condition 2: Natural speech pause after minimum duration
        val reachedSilencePause = currentBufferSize >= minChunkBytes && consecutiveSilenceFrames >= requiredSilenceFrames

        if (reachedHardLimit || reachedSilencePause) {
            val chunk = emitChunk(isFinal = false)
            return Pair(chunk, rms)
        }

        return Pair(null, rms)
    }

    /**
     * Flushes remaining audio in buffer when recording ends.
     */
    @Synchronized
    fun flush(): AudioChunk? {
        if (pcmBuffer.size() == 0) return null
        return emitChunk(isFinal = true)
    }

    private fun emitChunk(isFinal: Boolean): AudioChunk {
        val pcm = pcmBuffer.toByteArray()
        val startSec = chunkStartByteOffset.toDouble() / bytesPerSecond
        val endSec = (chunkStartByteOffset + pcm.size).toDouble() / bytesPerSecond

        pcmBuffer.reset()
        consecutiveSilenceFrames = 0

        return AudioChunk(
            pcmData = pcm,
            startTimeSec = startSec,
            endTimeSec = endSec,
            isFinal = isFinal
        )
    }

    companion object {
        fun calculateRms(pcmBytes: ByteArray): Double {
            if (pcmBytes.size < 2) return 0.0
            var sumSquare = 0.0
            val numSamples = pcmBytes.size / 2

            for (i in 0 until numSamples) {
                val byteLow = pcmBytes[i * 2].toInt() and 0xFF
                val byteHigh = pcmBytes[i * 2 + 1].toInt()
                val sample = (byteHigh shl 8) or byteLow
                sumSquare += sample * sample
            }

            return sqrt(sumSquare / numSamples)
        }
    }
}
