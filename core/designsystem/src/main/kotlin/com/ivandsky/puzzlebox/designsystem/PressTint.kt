package com.ivandsky.puzzlebox.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.unit.dp

/**
 * The tint half of the flat press (nav-rethink B1): a held
 * press on [interactionSource] fades a [tint] wash over the surface, clipped to [shape], up to
 * [MotionTokens.PressTintAlpha] over [MotionTokens.PressDuration], and out again on release.
 * Reduced motion shows the wash at once: it is state, not motion. Place it after the surface's own
 * background so the wash covers the face; pair it with [pressScale] for the scale half.
 */
@Composable
fun Modifier.pressTint(
    interactionSource: InteractionSource,
    shape: Shape,
    tint: Color = LocalContentColor.current,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val reduced = rememberReducedMotion()
    val alpha by animateFloatAsState(
        targetValue = if (pressed) MotionTokens.PressTintAlpha else 0f,
        animationSpec = if (reduced) snap() else tween(MotionTokens.PressDuration, easing = MotionTokens.EasingStandard),
        label = "press-tint",
    )
    return drawWithContent {
        drawContent()
        if (alpha > 0f) {
            drawOutline(shape.createOutline(size, layoutDirection, this), tint, alpha)
        }
    }
}

// Preview: a flat pill at rest; the wash itself is touch-driven.
@PuzzleBoxPreviews
@Composable
internal fun PressTintPreview() {
    PuzzleBoxTheme {
        val interaction = remember { MutableInteractionSource() }
        Box(
            Modifier
                .size(width = 96.dp, height = 40.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, PillShape)
                .pressTint(interaction, PillShape),
        )
    }
}
