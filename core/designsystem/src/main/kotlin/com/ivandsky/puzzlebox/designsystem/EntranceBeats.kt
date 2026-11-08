package com.ivandsky.puzzlebox.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode

/**
 * The Results entrance as one ordered list of beats (ms after first composition): the glyph
 * stamps, the time counts up, the pill and best line rise in, the supporting blocks follow,
 * the actions come last. Blocks inside one beat add [MotionTokens.EnterStagger] per index.
 */
object ResultBeats {
    const val Glyph = 0
    const val Time = 1
    const val Supporting = 2
    const val Blocks = 3
    const val Actions = 4

    val DelaysMs: List<Int> = listOf(0, 120, 520, 640, 840)
}

/** Start delay of block [index] in [beat]; reduced motion starts every block at once. */
fun beatDelayMs(beat: Int, index: Int = 0, reduced: Boolean = false): Int {
    if (reduced) return 0
    val stagger = index.coerceIn(0, MotionTokens.EnterStaggerCap) * MotionTokens.EnterStagger
    return ResultBeats.DelaysMs[beat] + stagger
}

/**
 * Lifts a block in: fade plus a [MotionTokens.EnterRise] rise over [MotionTokens.EnterDuration]
 * on a decelerate curve, once its [beat] delay has passed. Reduced motion: one plain
 * [MotionTokens.ReducedFadeDuration] fade, no delay, no travel. Previews show the end state.
 * The layer alpha clips paint outside the block's bounds until the fade ends.
 */
fun Modifier.resultBeat(beat: Int, index: Int = 0): Modifier = composed {
    val settled = LocalInspectionMode.current
    val reduced = rememberReducedMotion()
    val progress = remember { Animatable(if (settled) 1f else 0f) }
    LaunchedEffect(progress, reduced) {
        val spec = if (reduced) {
            tween<Float>(MotionTokens.ReducedFadeDuration)
        } else {
            tween(
                durationMillis = MotionTokens.EnterDuration,
                delayMillis = beatDelayMs(beat, index),
                easing = MotionTokens.EasingEmphasizedDecelerate,
            )
        }
        progress.animateTo(1f, spec)
    }
    val rise = with(LocalDensity.current) { MotionTokens.EnterRise.toPx() }
    graphicsLayer {
        alpha = progress.value
        if (!reduced) translationY = (1f - progress.value) * rise
    }
}
