package com.ivandsky.puzzlebox.designsystem

import androidx.compose.ui.graphics.Color

/**
 * One accent slot's resolved colours in the current theme: [accent] fills (tile glyph,
 * the game's CTA, picker edge, badges) with [onAccent] on top; [container] is the
 * quiet tint (glyph wells, result hero) with [onContainer] on top.
 */
data class GameAccent(
    val accent: Color,
    val onAccent: Color,
    val container: Color,
    val onContainer: Color,
)
