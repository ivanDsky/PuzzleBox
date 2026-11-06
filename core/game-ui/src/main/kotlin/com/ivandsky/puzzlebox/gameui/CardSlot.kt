package com.ivandsky.puzzlebox.gameui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion

/**
 * One card text line as a fixed [height] box, so a value change never moves a
 * neighbouring line (hub-card §Text slots). Like [PlayCaptionSlot], [value] changes
 * cross-fade in place (opacity only, no size transform) on MotionTokens short; reduced
 * motion swaps instantly.
 */
@Composable
fun <T> CardSlot(value: T, height: Dp, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    val reducedMotion = rememberReducedMotion()
    Box(modifier.fillMaxWidth().height(height), contentAlignment = Alignment.CenterStart) {
        if (reducedMotion) {
            content(value)
        } else {
            Crossfade(
                targetState = value,
                animationSpec = tween(MotionTokens.DurationShort, easing = MotionTokens.EasingStandard),
                label = "card-slot",
            ) { shown -> content(shown) }
        }
    }
}

// Preview: a filled slot over an empty one; both keep their height.
@PuzzleBoxPreviews
@Composable
internal fun CardSlotPreview() {
    PuzzleBoxTheme {
        Column(Modifier.padding(Spacing.md)) {
            listOf("#411 · Best 1:35", null).forEach { text ->
                CardSlot(value = text, height = 20.dp) { shown ->
                    Text(text = shown.orEmpty(), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
