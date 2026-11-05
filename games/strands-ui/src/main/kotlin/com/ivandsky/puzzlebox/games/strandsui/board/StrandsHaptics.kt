package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.ivandsky.puzzlebox.games.strandsui.Trace
import com.ivandsky.puzzlebox.games.strandsui.TraceStep
import com.ivandsky.puzzlebox.games.strandsui.VerdictKind
import com.ivandsky.puzzlebox.games.strandsui.WordSlot

/**
 * The buzz for one trace step, read from what the controller made of it: a tick as a letter
 * joins, then on submit the verdict's own feel. NYT's web game has none; these follow the
 * Android vocabulary: [HapticFeedbackType.Confirm] for a theme word, the stronger
 * [HapticFeedbackType.LongPress] for the spangram, [HapticFeedbackType.Reject] for a miss.
 * A non-theme word earns a hint third, not a word, so it gets only the letter tick.
 * Null when nothing changed (a step the controller ignored, a clear, a shrink).
 */
internal object StrandsHaptics {

    fun forStep(step: TraceStep, traceBefore: Trace, traceAfter: Trace, slotBefore: WordSlot, slotAfter: WordSlot): HapticFeedbackType? =
        when (step) {
            is TraceStep.Updated ->
                HapticFeedbackType.TextHandleMove.takeIf { traceAfter.path.size > traceBefore.path.size }
            is TraceStep.Submit -> (slotAfter as? WordSlot.Verdict)?.takeIf { it != slotBefore }?.let { verdict ->
                when (verdict.kind) {
                    VerdictKind.Theme -> HapticFeedbackType.Confirm
                    VerdictKind.Spangram -> HapticFeedbackType.LongPress
                    VerdictKind.Reject -> HapticFeedbackType.Reject
                    VerdictKind.NonTheme -> HapticFeedbackType.TextHandleMove
                }
            }
            TraceStep.Cleared -> null
        }
}
