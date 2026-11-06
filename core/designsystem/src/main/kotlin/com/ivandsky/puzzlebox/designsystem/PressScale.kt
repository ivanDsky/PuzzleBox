package com.ivandsky.puzzlebox.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * The house press feedback for a pressable surface: [MotionTokens.PressScale] on the default
 * spring while [interactionSource] is pressed; [reduced] motion never scales.
 */
@Composable
fun Modifier.pressScale(interactionSource: MutableInteractionSource, reduced: Boolean): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduced) MotionTokens.PressScale else 1f,
        animationSpec = spring(MotionTokens.SpringDampingDefault, MotionTokens.SpringStiffnessDefault),
        label = "press-scale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

// Preview: a pill-shaped surface at rest; the scale itself is touch-driven.
@PuzzleBoxPreviews
@Composable
internal fun PressScalePreview() {
    PuzzleBoxTheme {
        val interaction = remember { MutableInteractionSource() }
        Box(
            Modifier
                .pressScale(interaction, reduced = false)
                .size(width = 96.dp, height = 40.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, PillShape),
        )
    }
}
