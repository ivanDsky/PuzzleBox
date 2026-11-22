package com.ivandsky.puzzlebox.designsystem.community

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.pressTint

/**
 * The press feedback of a community card: scale to [scale] over [ShelfCardDefaults.PressMillis] while
 * [interactionSource] is pressed. Reduced motion never scales and has no tween; pair it with
 * [cardPressWash] so the press is still felt.
 */
@Composable
internal fun Modifier.cardPressScale(interactionSource: InteractionSource, scale: Float, reduced: Boolean): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val current by animateFloatAsState(
        targetValue = ShelfCardDefaults.pressTarget(pressed, reduced, scale),
        animationSpec = tween(ShelfCardDefaults.pressMillis(reduced), easing = MotionTokens.EasingStandard),
        label = "card-press",
    )
    return graphicsLayer {
        scaleX = current
        scaleY = current
    }
}

/** What reduced motion shows instead of the scale: the house wash over [shape] while pressed. Motion on does nothing. */
@Composable
internal fun Modifier.cardPressWash(interactionSource: InteractionSource, reduced: Boolean, shape: Shape): Modifier =
    if (reduced) pressTint(interactionSource, shape, MaterialTheme.colorScheme.onSurface) else this
