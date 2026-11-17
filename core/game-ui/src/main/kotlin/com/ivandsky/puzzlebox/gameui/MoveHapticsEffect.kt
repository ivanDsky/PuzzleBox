package com.ivandsky.puzzlebox.gameui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme

/**
 * Plays a game's move haptics (S2): each change of [snapshot] goes to [classify] with the snapshot before it, and
 * the answer plays as the soft tick for [MoveHaptic.Placed] or the soft buzz for [MoveHaptic.Rejected]. The snapshot
 * as first composed (a restore, a rotation) is the baseline and plays nothing; null (undo, a hint, a fresh board)
 * plays nothing. Only with [LocalPlayFeel] on.
 */
@Composable
fun <S> MoveHapticsEffect(snapshot: S, classify: (before: S, after: S) -> MoveHaptic?) {
    if (!LocalPlayFeel.current) return
    val haptics = LocalHapticFeedback.current
    val seen = remember { Baseline(snapshot) }
    SideEffect {
        val before = seen.snapshot
        seen.snapshot = snapshot
        if (before != snapshot) classify(before, snapshot)?.let { haptics.performHapticFeedback(it.feedbackType()) }
    }
}

private class Baseline<S>(var snapshot: S)

private fun MoveHaptic.feedbackType(): HapticFeedbackType = when (this) {
    MoveHaptic.Placed -> HapticFeedbackType.TextHandleMove
    MoveHaptic.Rejected -> HapticFeedbackType.Reject
}

@PuzzleBoxPreviews
@Composable
internal fun MoveHapticsEffectPreview() {
    PuzzleBoxTheme {
        MoveHapticsEffect(snapshot = 0) { _, _ -> null }
    }
}
