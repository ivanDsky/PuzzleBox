package com.ivandsky.puzzlebox.games.tango.engine

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle

object TangoBoundedSolution {

    /** Search nodes allowed; a 6×6 daily needs a handful, so this only guarantees the call never hangs. */
    const val POLL_LIMIT: Int = 100_000

    /** [puzzle]'s one solution within [pollLimit] search nodes; null when exhausted or not unique. */
    fun of(puzzle: TangoPuzzle, pollLimit: Int = POLL_LIMIT): List<TangoCell>? {
        var polls = 0
        val outcome = TangoUniqueness.bounded(puzzle, limit = 2, budget = Budget { polls++ >= pollLimit })
        return (outcome as? TangoSearchOutcome.Done)?.solutions?.singleOrNull()
    }
}
