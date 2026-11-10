package com.ivandsky.puzzlebox.compete.reveal

import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * The reveal's buzzes, read from the change between two consecutive machine states (the StrandsHaptics pattern): a
 * soft tick as the VS card locks in and at each puzzle's verdict, a [HapticFeedbackType.Confirm] for the viewer's
 * puzzle, and the strongest, [HapticFeedbackType.LongPress], as the viewer wins the duel. Nothing buzzes for a loss,
 * a draw, a puzzle nobody wins, or a reveal that ends by skip or reduced motion.
 *
 * The verdicts are read from the score, not from `locked`, so a frame that crosses a lock at a leg boundary still
 * buzzes it. A frame that crosses several beats buzzes the strongest once.
 */
internal object RevealHaptics {

    fun between(before: RevealState, after: RevealState): HapticFeedbackType? {
        if (before.phase == RevealPhase.End || after.phase == RevealPhase.End) return null
        val beats = buildList {
            if (before.phase == RevealPhase.VsIntro && after.phase is RevealPhase.Leg) add(HapticFeedbackType.TextHandleMove)
            if (after.myScore > before.myScore) add(HapticFeedbackType.Confirm)
            if (after.theirScore > before.theirScore) add(HapticFeedbackType.TextHandleMove)
            if (before.phase is RevealPhase.Leg && after.phase == RevealPhase.Score && after.myScore > after.theirScore) {
                add(HapticFeedbackType.LongPress)
            }
        }
        return beats.maxByOrNull(::strength)
    }

    private fun strength(type: HapticFeedbackType): Int = when (type) {
        HapticFeedbackType.LongPress -> 3
        HapticFeedbackType.Confirm -> 2
        else -> 1
    }
}
