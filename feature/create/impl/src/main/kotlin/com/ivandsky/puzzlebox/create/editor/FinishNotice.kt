package com.ivandsky.puzzlebox.create.editor

import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.game.editor.FinishReadiness
import com.ivandsky.puzzlebox.game.editor.Verdict

/** What the Studio says after a Finish that did not do what was asked (E1, 90); [text] is the message, word for word. */
internal sealed interface FinishNotice {
    val text: String

    /** The completer gave up on a board that still has room: more pieces narrow the search. */
    data object NeedsMore : FinishNotice {
        override val text = "Not enough on the board to finish yet. Place a few more pieces and try again."
    }

    /** The completer gave up on a board that is full or nearly full (63b): only changing cells can help. */
    data object NoFix : FinishNotice {
        override val text = "Couldn't fix this board in time. Change a few highlighted cells and try again."
    }

    /** The board was finished, at [got] and not at the [wanted] level. */
    data class Missed(val got: Difficulty.Level, val wanted: Difficulty.Level) : FinishNotice {
        override val text = "Finished at ${got.name} \u2014 ${wanted.name} wasn't found in time. Press Finish to try again."
    }
}

/**
 * The notice for a completer that gave up on the author's pieces, by cause. The board still has
 * room while the completer's [readiness] is NeedsMore or the pieces' [verdict] is Incomplete or
 * Undetermined: [FinishNotice.NeedsMore]. Any other verdict means the board already has its
 * answers, so placing more cannot help: [FinishNotice.NoFix].
 */
internal fun gaveUpNotice(readiness: FinishReadiness?, verdict: Verdict): FinishNotice =
    if (readiness is FinishReadiness.NeedsMore || verdict is Verdict.Incomplete || verdict == Verdict.Undetermined) {
        FinishNotice.NeedsMore
    } else {
        FinishNotice.NoFix
    }
