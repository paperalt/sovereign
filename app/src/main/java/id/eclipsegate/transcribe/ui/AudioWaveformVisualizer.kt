package id.eclipsegate.transcribe.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun AudioWaveformVisualizer(
    amplitudeSupplier: () -> FloatArray,
    barColor: Color = Color(0xFF38BDF8),
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(36.dp)
) {
    Canvas(modifier = modifier) {
        val amplitudes = amplitudeSupplier()
        if (amplitudes.isEmpty()) return@Canvas

        val barCount = amplitudes.size
        val totalWidth = size.width
        val barWidth = (totalWidth / barCount) * 0.65f
        val gap = (totalWidth - (barWidth * barCount)) / (barCount - 1).coerceAtLeast(1)
        val maxHeight = size.height

        val minBarHeight = 4.dp.toPx()
        for (i in 0 until barCount) {
            val amp = amplitudes[i]
            val normalizedAmp = if (amp > 0.05f) {
                amp.coerceIn(0.08f, 1.0f)
            } else {
                // Subtle organic baseline curve so idle state doesn't look like flat hyphens
                val progress = i.toFloat() / (barCount - 1).coerceAtLeast(1)
                (0.08f + 0.06f * kotlin.math.sin(progress * Math.PI).toFloat())
            }
            val barHeight = (maxHeight * normalizedAmp).coerceAtLeast(minBarHeight)
            val x = i * (barWidth + gap)
            val y = (maxHeight - barHeight) / 2f

            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
