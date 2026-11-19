package com.ivandsky.puzzlebox.resultui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion

/** Crossfades [content] when [over] flips: the label before the target gives way to the one after it. */
@Composable
fun CrossingLabel(over: Boolean, modifier: Modifier = Modifier, content: @Composable (over: Boolean) -> Unit) {
    Crossfade(
        modifier = modifier,
        targetState = over,
        animationSpec = if (rememberReducedMotion()) snap() else tween(MotionTokens.DurationExit, easing = LinearEasing),
        label = "timer-label",
        content = content,
    )
}

@PuzzleBoxPreviews
@Composable
private fun CrossingLabelPreview() {
    PuzzleBoxTheme {
        Column(Modifier.padding(Spacing.lg)) {
            CrossingLabel(over = false) { Text(selfTimerLabel(it, "0:46")) }
            CrossingLabel(over = true) { Text(selfTimerLabel(it, "0:08")) }
        }
    }
}
