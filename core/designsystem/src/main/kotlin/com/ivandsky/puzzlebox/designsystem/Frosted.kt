package com.ivandsky.puzzlebox.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.roundToInt

// The blur moves in this many steps, each one effect built once: a scrub passes every opacity, not every blur.
private const val FrostSteps = 8

/**
 * The frost of a [MorphCell] layer with its clock in the caller's hands: drawn at the opacity [shown] says (0 to 1),
 * blurred up to [MotionTokens.DayMorphBlur] as it goes. [shown] is read only when the layer is drawn, so a drag that
 * scrubs it recomposes and lays out nothing. Nothing is clipped; fully shown it is drawn as it is.
 */
fun Modifier.frosted(shown: () -> Float): Modifier {
    val effects = arrayOfNulls<RenderEffect>(FrostSteps + 1)
    return graphicsLayer {
        val opacity = shown().coerceIn(0f, 1f)
        val step = ((1f - opacity) * FrostSteps).roundToInt()
        alpha = opacity
        renderEffect = if (step == 0) {
            null
        } else {
            effects[step] ?: (MotionTokens.DayMorphBlur.toPx() * step / FrostSteps).let { radius ->
                BlurEffect(radius, radius, TileMode.Decal).also { effects[step] = it }
            }
        }
    }
}
