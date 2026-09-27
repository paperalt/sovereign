package org.sovereign.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object SovereignColors {
    val OnyxBlack = Color(0xFF0A0D12)
    val DarkSlate = Color(0xFF141A22)
    val CardBackground = Color(0xFF0F151E)
    val CardElevated = Color(0xFF161D2B)
    val SteelBorder = Color(0xFF283342)
    val BorderSubtle = Color(0xFF1E2632)

    val TextPrimary = Color(0xFFF0F4F8)
    val TextSecondary = Color(0xFF94A3B8)
    val TextMuted = Color(0xFF64748B)

    val AccentPrimary = Color(0xFF38BDF8)
    val AccentDeep = Color(0xFF0284C7)
    val EmeraldSuccess = Color(0xFF10B981)
    val AmberWarning = Color(0xFFF59E0B)
    val CrimsonAlert = Color(0xFFEF4444)
    val IndigoAccent = Color(0xFF818CF8)
}

object SovereignGradients {
    // Primary Action Gradients
    val CyanAccent = Brush.horizontalGradient(
        colors = listOf(SovereignColors.AccentPrimary, SovereignColors.AccentDeep)
    )

    val CyanVertical = Brush.verticalGradient(
        colors = listOf(SovereignColors.AccentPrimary, SovereignColors.AccentDeep)
    )

    // Hairline Border Sheen Gradients (Top-lit edge highlights)
    val CyanHairlineBorder = Brush.verticalGradient(
        colors = listOf(SovereignColors.AccentPrimary.copy(alpha = 0.45f), SovereignColors.SteelBorder)
    )

    val NeutralHairlineBorder = Brush.verticalGradient(
        colors = listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.03f))
    )

    val AmberHairlineBorder = Brush.verticalGradient(
        colors = listOf(SovereignColors.AmberWarning.copy(alpha = 0.50f), SovereignColors.SteelBorder)
    )

    val EmeraldHairlineBorder = Brush.verticalGradient(
        colors = listOf(SovereignColors.EmeraldSuccess.copy(alpha = 0.50f), SovereignColors.SteelBorder)
    )

    val CrimsonHairlineBorder = Brush.verticalGradient(
        colors = listOf(SovereignColors.CrimsonAlert.copy(alpha = 0.50f), SovereignColors.SteelBorder)
    )

    // Ambient Surface Gradients
    val ElevatedSurface = Brush.verticalGradient(
        colors = listOf(SovereignColors.CardElevated, SovereignColors.CardBackground)
    )

    val HeaderSurface = Brush.verticalGradient(
        colors = listOf(SovereignColors.DarkSlate, Color(0xFF0D1219))
    )

    val ActivePillSurface = Brush.horizontalGradient(
        colors = listOf(Color(0xFF0E2A3B), Color(0xFF0A1F2C))
    )

    val SelectionHeaderSurface = Brush.horizontalGradient(
        colors = listOf(Color(0xFF0F2236), Color(0xFF0C1826))
    )
}
