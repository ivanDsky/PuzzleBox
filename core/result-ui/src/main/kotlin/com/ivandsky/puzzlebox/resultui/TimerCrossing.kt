package com.ivandsky.puzzlebox.resultui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.LocalTimerFill
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion

/*
 * Crossing past you's time on a self-duel leg (motion.md § Timer crossing zero), shared by the bar timer and the
 * tablet time card so both cross the same way: the label crossfades over DurationExit (CrossingLabel), the fill
 * tweens to the tertiary hue; with reduced motion both swap instantly.
 */

/** The copy for a clock counting toward past you's time: "0:46 left", then "Over by 0:08". */
fun selfTimerLabel(over: Boolean, clock: String): String = if (over) "Over by $clock" else "$clock left"

/** The fill colour of a timer bar: the timer hue until [over], then tertiary, tweened. */
@Composable
fun rememberCrossingFill(over: Boolean): Color {
    val fill by animateColorAsState(
        targetValue = if (over) MaterialTheme.colorScheme.tertiary else LocalTimerFill.current,
        animationSpec = if (rememberReducedMotion()) snap() else tween(FILL_TWEEN_MS, easing = MotionTokens.EasingStandard),
        label = "timer-fill",
    )
    return fill
}

/** motion.md: Timer crossing zero — 200 ms standard easing. */
private const val FILL_TWEEN_MS = 200

@PuzzleBoxPreviews
@Composable
private fun CrossingFillPreview() {
    PuzzleBoxTheme {
        FlatProgressBar(progress = { 1f }, color = rememberCrossingFill(over = true), modifier = Modifier.width(160.dp).padding(Spacing.lg))
    }
}
