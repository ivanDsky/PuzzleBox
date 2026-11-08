package com.ivandsky.puzzlebox.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode

internal const val COUNT_UP_DURATION_MS = MotionTokens.CountUpDuration

/** The value shown at [progress] (0..1) of a count-up to [target]. */
internal fun countUpValue(target: Long, progress: Float): Long = (target * progress).toLong()

/**
 * A number that rolls from 0 to [target] over [MotionTokens.CountUpDuration] on
 * [MotionTokens.EasingEmphasizedDecelerate], after [delayMs]. Reduced motion and previews
 * show [target] at once. A new [target] rolls again from 0.
 */
@Composable
fun rememberCountUp(target: Long, delayMs: Int = 0): Long {
    val instant = rememberReducedMotion() || LocalInspectionMode.current
    val progress = remember(target) { Animatable(if (instant) 1f else 0f) }
    LaunchedEffect(progress, instant) {
        if (instant) {
            progress.snapTo(1f)
        } else {
            progress.animateTo(
                1f,
                tween(COUNT_UP_DURATION_MS, delayMillis = delayMs, easing = MotionTokens.EasingEmphasizedDecelerate),
            )
        }
    }
    return countUpValue(target, progress.value)
}

// Preview — Studio tooling only. The count-up's end state (a preview never animates): the
// hero time at rest, tabular.
@PuzzleBoxPreviews
@Composable
internal fun CountUpPreview() {
    PuzzleBoxTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.background).padding(Spacing.md)) {
            Text(formatDuration(rememberCountUp(95_000L)), style = MaterialTheme.typography.displayLarge.tabular())
        }
    }
}
