package com.ivandsky.puzzlebox.designsystem

import androidx.compose.ui.graphics.Color

/**
 * One accent slot's resolved colours in the current theme: [accent] fills (tile glyph,
 * the game's CTA, picker edge, badges) with [onAccent] on top; [container] is the
 * quiet tint (glyph wells, result hero) with [onContainer] on top; [lip] is the darker
 * same-hue bottom edge a chunky button or tile sits on.
 */
data class GameAccent(
    val accent: Color,
    val onAccent: Color,
    val container: Color,
    val onContainer: Color,
    val lip: Color,
)
