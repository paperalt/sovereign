package org.sovereign.app.audio

import java.util.Locale

object TranscriptStitcher {

    /**
     * Stitches two contiguous transcript segments, pruning any overlapping words
     * at the boundary (Longest Suffix-Prefix Matching).
     */
    fun stitch(previousText: String, currentText: String): String {
        val prev = previousText.trim()
        val curr = currentText.trim()

        if (prev.isBlank()) return curr
        if (curr.isBlank()) return prev

        val prevWords = prev.split("\\s+".toRegex()).filter { it.isNotBlank() }
        val currWords = curr.split("\\s+".toRegex()).filter { it.isNotBlank() }

        if (prevWords.isEmpty()) return curr
        if (currWords.isEmpty()) return prev

        // Check for boundary overlap from 4 words down to 1 word
        val maxOverlap = minOf(4, prevWords.size, currWords.size)
        for (overlapSize in maxOverlap downTo 1) {
            val tail = prevWords.takeLast(overlapSize).joinToString(" ")
            val head = currWords.take(overlapSize).joinToString(" ")

            if (normalize(tail) == normalize(head)) {
                // Suffix-prefix match found; append remaining non-duplicate words
                val nonDuplicateCurr = currWords.drop(overlapSize).joinToString(" ")
                return if (nonDuplicateCurr.isNotBlank()) "$prev $nonDuplicateCurr" else prev
            }
        }

        // No overlap detected; standard continuation
        return "$prev $curr"
    }

    private fun normalize(str: String): String {
        return str.lowercase(Locale.ROOT)
            .replace("[^a-zA-Z0-9 ]".toRegex(), "")
            .trim()
    }
}
