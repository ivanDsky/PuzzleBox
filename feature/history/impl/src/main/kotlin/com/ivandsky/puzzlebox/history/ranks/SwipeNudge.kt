package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PillShape
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.designsystem.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.delay

/** The hint shows this long after the hub opens, so the board has landed first, and stays this long. */
private const val NUDGE_DELAY_MS = 1_800L
private const val NUDGE_HOLD_MS = 2_600L

/** The current dot swells this far at the peak of its one pulse; the shared pulse token is too faint for a 6 dp dot. */
private const val DOT_PULSE_PEAK = 1.7f

/** The hint for a board at [position] of [boards]: the next board's name, or the previous one's from the last. Null with nothing to swipe to. */
internal fun nudgeText(boards: List<BoardPlace>, position: Int): String? = when {
    position !in boards.indices || boards.size < 2 -> null
    position < boards.lastIndex -> "Swipe for ${boards[position + 1].name} ›"
    else -> "‹ Swipe for ${boards[position - 1].name}"
}

/** The current dot's scale at [progress] (0..1) of its pulse: 1 up to [DOT_PULSE_PEAK] and back. */
internal fun dotPulseScale(progress: Float): Float = 1f + (DOT_PULSE_PEAK - 1f) * sin(progress * PI.toFloat())

/** The nudge's two moving parts, both read in the draw phase only: how far the hint [pill] has come in (0..1) and the dot's [pulse] progress (0..1). */
@Stable
internal class SwipeNudgeState(pillAt: Float = 0f) {
    val pill = Animatable(pillAt)
    val pulse = Animatable(0f)
}

/**
 * Plays the nudge once while [show]: the hint comes in, the current dot pulses, the hint goes out, then [onShown]
 * (the caller records it, so [show] turns false and it never plays again). Without [show] a half-played nudge fades
 * out. Reduced motion (and previews) keep the hint and drop the pulse and the rise.
 */
@Composable
internal fun rememberSwipeNudge(show: Boolean, onShown: () -> Unit): SwipeNudgeState {
    val state = remember { SwipeNudgeState() }
    val shown by rememberUpdatedState(onShown)
    val reduced = rememberReducedMotion()
    val still = LocalInspectionMode.current
    LaunchedEffect(show) {
        if (!show) {
            state.pill.animateTo(0f, tween(MotionTokens.DurationExit, easing = MotionTokens.EasingStandard))
            state.pulse.snapTo(0f)
            return@LaunchedEffect
        }
        if (still) return@LaunchedEffect
        delay(NUDGE_DELAY_MS)
        state.pill.animateTo(1f, tween(if (reduced) MotionTokens.ReducedFadeDuration else MotionTokens.EnterDuration, easing = MotionTokens.EasingEmphasizedDecelerate))
        if (!reduced) {
            state.pulse.animateTo(1f, tween(MotionTokens.PulseDuration, easing = LinearEasing))
            state.pulse.snapTo(0f)
        }
        delay(NUDGE_HOLD_MS)
        state.pill.animateTo(0f, tween(MotionTokens.DurationExit, easing = MotionTokens.EasingStandard))
        shown()
    }
    return state
}

/** The swipe hint [text], faded and risen into place by [state]; it takes no space and no touches. */
@Composable
internal fun SwipeNudgePill(text: String?, state: SwipeNudgeState, modifier: Modifier = Modifier) {
    val visible by remember { derivedStateOf { state.pill.value > 0f } }
    if (text == null || !visible) return
    val rise = with(LocalDensity.current) { MotionTokens.EnterRise.toPx() }
    val reduced = rememberReducedMotion()
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.inverseOnSurface,
        modifier = modifier
            .testTag("ranks.nudge")
            .graphicsLayer {
                alpha = state.pill.value
                translationY = if (reduced) 0f else (1f - state.pill.value) * rise
            }
            .background(MaterialTheme.colorScheme.inverseSurface, PillShape)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@PuzzleBoxPreviews
@Composable
private fun SwipeNudgePillPreview() {
    RanksPreviewHost {
        val state = remember { SwipeNudgeState(pillAt = 1f) }
        SwipeNudgePill("Swipe for Zip ›", state, Modifier.padding(Spacing.lg))
    }
}
