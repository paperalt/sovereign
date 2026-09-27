package org.sovereign.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun AudioWaveformVisualizer(
    amplitudeSupplier: () -> FloatArray,
    barTopColor: Color = Color(0xFF38BDF8),
    barBottomColor: Color = Color(0xFF0284C7),
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(44.dp)
) {
    Canvas(modifier = modifier) {
        val amplitudes = amplitudeSupplier()
        if (amplitudes.isEmpty()) return@Canvas

        val barCount = amplitudes.size
        val totalWidth = size.width
        val maxHeight = size.height

        // Hardware oscilloscope center baseline guideline
        drawLine(
            color = Color.White.copy(alpha = 0.06f),
            start = Offset(0f, maxHeight / 2f),
            end = Offset(totalWidth, maxHeight / 2f),
            strokeWidth = 1.dp.toPx()
        )

        val barWidth = (totalWidth / barCount) * 0.62f
        val gap = (totalWidth - (barWidth * barCount)) / (barCount - 1).coerceAtLeast(1)
        val minBarHeight = 3.dp.toPx()

        for (i in 0 until barCount) {
            val amp = amplitudes[i]
            val normalizedAmp = if (amp > 0.055f) {
                amp.coerceIn(0.08f, 1.0f)
            } else {
                // Organic breathing baseline curve when silent so the meter feels live and responsive
                val progress = i.toFloat() / (barCount - 1).coerceAtLeast(1)
                (0.06f + 0.05f * kotlin.math.sin(progress * Math.PI).toFloat())
            }

            val barHeight = (maxHeight * normalizedAmp).coerceIn(minBarHeight, maxHeight)
            val x = i * (barWidth + gap)
            val y = (maxHeight - barHeight) / 2f

            // Leftmost edge fade for smooth historical stream transition
            val fadeRatio = (i.toFloat() / 4.coerceAtLeast(1)).coerceIn(0.25f, 1.0f)
            val topColorWithFade = barTopColor.copy(alpha = barTopColor.alpha * fadeRatio)
            val bottomColorWithFade = barBottomColor.copy(alpha = barBottomColor.alpha * fadeRatio)

            val brush = Brush.verticalGradient(
                colors = listOf(topColorWithFade, bottomColorWithFade),
                startY = y,
                endY = y + barHeight
            )

            drawRoundRect(
                brush = brush,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
