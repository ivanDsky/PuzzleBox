package com.ivandsky.puzzlebox.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

private const val TEXT_CONTRAST = 4.5f

/** The WCAG 2 contrast ratio of [foreground] on [background], 1:1 to 21:1, whichever of the two is lighter. */
fun contrastRatio(foreground: Color, background: Color): Float {
    val top = foreground.luminance() + 0.05f
    val bottom = background.luminance() + 0.05f
    return if (top > bottom) top / bottom else bottom / top
}

/**
 * The ink for small text on [fill]: [preferred] (the theme's on-fill) where it reads at 4.5:1, else [OnBrightFill].
 * Light mode's white fails on every bright accent, so deciding from the fill keeps the white where the fill is dark
 * (the neutral card) and never leaves a game's accent unreadable.
 */
fun readableInk(fill: Color, preferred: Color): Color =
    if (contrastRatio(preferred, fill) >= TEXT_CONTRAST) preferred else OnBrightFill
