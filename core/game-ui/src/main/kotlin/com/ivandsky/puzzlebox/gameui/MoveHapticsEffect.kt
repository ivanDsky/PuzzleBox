package com.ivandsky.puzzlebox.gameui

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
 * [snapshot] is read in the effect, never in the caller's composition, so a drag game does not recompose per step.
 * A change is a snapshot unequal to the last one. Where refused input leaves the session untouched, [S] must carry a
 * counter of refusals, never the last refusal itself: a second refusal of the same kind would equal the first.
 */
@Composable
fun <S> MoveHapticsEffect(snapshot: () -> S, classify: (before: S, after: S) -> MoveHaptic?) {
    val haptics = LocalHapticFeedback.current
    val read by rememberUpdatedState(snapshot)
    val classified by rememberUpdatedState(classify)
    LaunchedEffect(haptics) {
        var previous = read()
        // The first emission is that same baseline.
        snapshotFlow { read() }.drop(1).collect { next ->
            classified(previous, next)?.let { haptics.performHapticFeedback(it.feedbackType()) }
            previous = next
        }
    }
}

private fun MoveHaptic.feedbackType(): HapticFeedbackType = when (this) {
    MoveHaptic.Placed -> HapticFeedbackType.TextHandleMove
    MoveHaptic.Rejected -> HapticFeedbackType.Reject
}

@PuzzleBoxPreviews
@Composable
internal fun MoveHapticsEffectPreview() {
    PuzzleBoxTheme {
        MoveHapticsEffect(snapshot = { 0 }) { _, _ -> null }
    }
}
