package com.ivandsky.puzzlebox.gameui

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import kotlinx.coroutines.flow.drop

/**
 * Plays a game's move haptics (S2): each change of [snapshot] goes to [classify] with the snapshot before it, and
 * the answer plays as the soft tick for [MoveHaptic.Placed] or the soft buzz for [MoveHaptic.Rejected]. The snapshot
 * the effect starts with (a restore, a rotation) is the baseline and plays nothing; null (undo, a hint, a fresh
 * board) plays nothing.
 *
 * A tick comes at most once per [MOVE_TICK_GAP_MS] of [now]: one within the gap of the last tick played is dropped,
 * so a fast drag ticks evenly instead of stacking into one buzz (#1686). A refusal always plays and leaves the gap
 * alone.
 *
 * [snapshot] is read in the effect, never in the caller's composition, so a drag game does not recompose per step.
 * A change is a snapshot unequal to the last one. Where refused input leaves the session untouched, [S] must carry a
 * counter of refusals, never the last refusal itself: a second refusal of the same kind would equal the first.
 */
@Composable
fun <S> MoveHapticsEffect(
    snapshot: () -> S,
    classify: (before: S, after: S) -> MoveHaptic?,
    now: () -> Long = SystemClock::uptimeMillis,
) {
    val haptics = LocalHapticFeedback.current
    val read by rememberUpdatedState(snapshot)
    val classified by rememberUpdatedState(classify)
    val clock by rememberUpdatedState(now)
    LaunchedEffect(haptics) {
        var previous = read()
        var lastTick: Long? = null
        // The first emission is that same baseline.
        snapshotFlow { read() }.drop(1).collect { next ->
            when (classified(previous, next)) {
                MoveHaptic.Placed -> {
                    val at = clock()
                    if (lastTick.let { it == null || at - it >= MOVE_TICK_GAP_MS }) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        lastTick = at
                    }
                }
                MoveHaptic.Rejected -> haptics.performHapticFeedback(HapticFeedbackType.Reject)
                null -> Unit
            }
            previous = next
        }
    }
}

/**
 * The shortest gap between two move ticks. A One UI tablet's 35 ms tick stacks into one buzz at a drag's
 * step rate; 80 ms is the spacing its user picked. Taps are further apart, so they all tick.
 */
internal const val MOVE_TICK_GAP_MS = 80L

@PuzzleBoxPreviews
@Composable
internal fun MoveHapticsEffectPreview() {
    PuzzleBoxTheme {
        MoveHapticsEffect(snapshot = { 0 }, classify = { _, _ -> null })
    }
}
