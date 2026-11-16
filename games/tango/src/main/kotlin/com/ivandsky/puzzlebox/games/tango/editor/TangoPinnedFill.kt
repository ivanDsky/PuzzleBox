package com.ivandsky.puzzlebox.games.tango.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.engine.TangoPropagation
import kotlin.random.Random

/**
 * A random valid full grid that agrees with a draft's givens and markers: the engine
 * generator's random fill (`TangoBoardGenerator`) with the author's pieces as constraints.
 * It is the uniqueness search over [TangoPropagation] with one change: the two values of a
 * branch cell are tried in random order, so the first grid reached differs from seed to seed.
 */
internal object TangoPinnedFill {
    /**
     * A full row-major grid holding every given and satisfying every marker of [pieces]; null
     * when no grid does or [budget] ran out first. [budget] is polled once per search node.
     */
    fun fill(pieces: TangoPuzzle, random: Random, budget: Budget): List<TangoCell>? {
        val state = TangoPropagation(pieces)
        var spent = false

        fun filled(): Boolean {
            if (spent) return false
            if (budget.exhausted()) {
                spent = true
                return false
            }
            if (!state.propagate() || !state.probe()) return false
            val idx = state.mostConstrained()
            if (idx < 0) return true
            val first = if (random.nextBoolean()) TangoCell.SUN else TangoCell.MOON
            val mark = state.mark()
            if (state.place(idx, first) && filled()) return true
            state.undoTo(mark)
            return state.place(idx, first.other) && filled()
        }
        return if (filled()) state.grid.map { it!! } else null
    }
}
