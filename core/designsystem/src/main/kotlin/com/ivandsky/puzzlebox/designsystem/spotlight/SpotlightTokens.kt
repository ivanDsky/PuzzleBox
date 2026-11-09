package com.ivandsky.puzzlebox.designsystem.spotlight

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Quiet Spotlight look (prototype 07). No new colour: board ink is onSurface, the culprit is the scheme's error,
 * the callout is inverseSurface, the dim is an overlay of the board surface. Reduced motion is the overlay's rule.
 */
object SpotlightTokens {
    const val DIM_ALPHA = 0.40f
    const val TARGET_GHOST_ALPHA = 0.28f
    const val EXCLUDED_ALPHA = 0.50f

    val HairlineWidth: Dp = 1.5.dp
    val CalloutRadius: Dp = 12.dp
    val CalloutTextSize: TextUnit = 14.sp
    val CalloutLineHeight: TextUnit = 18.sp

    const val FADE_MS = 120
    const val EXCLUDED_START_SCALE = 0.85f
    const val EXCLUDED_STAGGER_MS = 20
    const val EXCLUDED_MAX_STAGGERED = 3
    const val CALLOUT_START_SCALE = 0.94f
    const val PULSE_PEAK_SCALE = 1.12f
    const val PULSE_DELAY_MS = 160
    const val PULSE_DURATION_MS = 240
    const val TOTAL_MS = 400

    fun dimOverlay(boardSurface: Color): Color = boardSurface.copy(alpha = DIM_ALPHA)
    fun hairline(scheme: ColorScheme): Color = scheme.onSurface
    fun targetGhost(scheme: ColorScheme): Color = scheme.onSurface.copy(alpha = TARGET_GHOST_ALPHA)
    fun excluded(scheme: ColorScheme): Color = scheme.onSurface.copy(alpha = EXCLUDED_ALPHA)
    fun culprit(scheme: ColorScheme): Color = scheme.error
    fun culpritBadgeBar(scheme: ColorScheme): Color = scheme.onError
    fun calloutBubble(scheme: ColorScheme): Color = scheme.inverseSurface
    fun calloutText(scheme: ColorScheme): Color = scheme.inverseOnSurface
}
