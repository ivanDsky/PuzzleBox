package com.ivandsky.puzzlebox.train.round

import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.gameui.trainer.Snippet
import com.ivandsky.puzzlebox.train.domain.RecordAnswer

/** Where a round stands: being built, not buildable, on a snippet, or over. */
internal sealed interface RoundState {

    /** The snippets are being cut from the puzzles on the device. */
    data object Loading : RoundState

    /** No puzzle on the device has a step of the technique, so there is nothing to ask. */
    data object Empty : RoundState

    /**
     * Snippet [index] of [snippets] is up. [answer] is what the player did with it, or null while it waits for a move;
     * [right] counts the snippets answered Right so far, this one included once answered.
     */
    data class Question(
        val snippets: List<Snippet>,
        val index: Int,
        val answer: RecordAnswer.Answer?,
        val right: Int,
    ) : RoundState {
        val snippet: Snippet get() = snippets[index]
        val total: Int get() = snippets.size
        val isLast: Boolean get() = index == snippets.lastIndex
        val answered: Boolean get() = answer != null
    }

    /** The round is over: [right] of [total] snippets answered Right. */
    data class Summary(val right: Int, val total: Int) : RoundState
}

/** True for the one answer that counts as right: a move the target technique proves. */
internal val RecordAnswer.Answer.isRight: Boolean
    get() = this is RecordAnswer.Answer.Verdict && verdict == TrainerVerdict.Right
