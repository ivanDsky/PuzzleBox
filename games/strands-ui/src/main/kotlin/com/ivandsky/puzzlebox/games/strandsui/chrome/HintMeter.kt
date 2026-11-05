package com.ivandsky.puzzlebox.games.strandsui.chrome

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import com.ivandsky.puzzlebox.games.strandsui.HintUi
import com.ivandsky.puzzlebox.games.strandsui.StrandsMotion

private const val THIRDS = 3

/** How full the meter reads: thirds toward the next hint, or full while a hint is waiting. */
internal fun hintMeterFraction(hint: HintUi): Float =
    if (hint.available) 1f else hint.meterThirds.coerceIn(0, THIRDS).toFloat() / THIRDS

/**
 * HintMeterFill toward [target]: a reader of the drawn fraction. The first value is drawn
 * as-is; later ones fill over [StrandsMotion.HintMeterFill], or land at once under [reduceMotion].
 */
@Composable
internal fun rememberMeterFill(target: Float, reduceMotion: Boolean): () -> Float {
    val fill = remember { Animatable(target) }
    LaunchedEffect(target, reduceMotion) {
        if (reduceMotion) {
            fill.snapTo(target)
        } else {
            val pattern = StrandsMotion.HintMeterFill
            fill.animateTo(target, tween(pattern.enterDurationMillis, easing = pattern.enterEasing))
        }
    }
    return remember { { fill.value } }
}

/**
 * NYT's meter-in-a-button: [content] drawn twice, plain underneath and again on [fillColor]
 * clipped to [fraction] of the width from the start edge, so the label changes colour exactly
 * where the fill ends. [content] learns which layer it is drawing.
 */
@Composable
internal fun HintMeter(
    fraction: () -> Float,
    fillColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(onFill: Boolean) -> Unit,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        content(false)
        Box(
            Modifier
                .matchParentSize()
                // Read in the draw phase: the fill animates without recomposing the button.
                .drawWithContent {
                    val filled = size.width * fraction()
                    if (layoutDirection == LayoutDirection.Ltr) {
                        clipRect(right = filled) { this@drawWithContent.drawContent() }
                    } else {
                        clipRect(left = size.width - filled) { this@drawWithContent.drawContent() }
                    }
                }
                .background(fillColor)
                .testTag("strands.hint_meter"),
            contentAlignment = Alignment.Center,
        ) {
            content(true)
        }
    }
}
