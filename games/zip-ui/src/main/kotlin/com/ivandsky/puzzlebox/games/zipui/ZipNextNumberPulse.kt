package com.ivandsky.puzzlebox.games.zipui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import kotlinx.coroutines.flow.collectLatest

/** One fade of the ring, out or back: two make the 1.2 s cycle. */
private const val PULSE_HALF_CYCLE_MILLIS = 600

/** How faint the ring gets at the far end of a pulse; it never disappears. */
private const val PULSE_LOW_ALPHA = 0.35f

/**
 * The opacity of the ring on the next number to reach, for the live play board (nav-rethink
 * B3, user note 2026-11-16): a gentle loop between full and faint, about 1.2 s a cycle, so the
 * eye finds the number without the ring ever changing size. It holds at full while [held]
 * (a drag is down) and starts again on lift. With [enabled] false (reduced motion, or no
 * number left to reach) or a board geometry without the ring, it stays at full and nothing
 * runs.
 *
 * Returns a reader to call in the draw phase: the pulse repaints the one cell that draws
 * the ring and recomposes nothing. [held] is read as snapshot state.
 *
 * This is the one endless animation in a game module (konsist S6 allowlist): it is the
 * board's idle cue, not a response to an input.
 */
@Composable
internal fun rememberNextNumberPulse(held: () -> Boolean, enabled: Boolean): () -> Float {
    val runs = enabled
    val isHeld by rememberUpdatedState(held)
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(alpha, runs) {
        if (!runs) {
            alpha.snapTo(1f)
            return@LaunchedEffect
        }
        snapshotFlow { isHeld() }.collectLatest { down ->
            if (down) {
                alpha.animateTo(1f, tween(MotionTokens.DurationShort))
            } else {
                alpha.animateTo(
                    PULSE_LOW_ALPHA,
                    infiniteRepeatable(
                        tween(PULSE_HALF_CYCLE_MILLIS, easing = MotionTokens.EasingStandard),
                        RepeatMode.Reverse,
                    ),
                )
            }
        }
    }
    return remember(alpha) { { alpha.value } }
}
