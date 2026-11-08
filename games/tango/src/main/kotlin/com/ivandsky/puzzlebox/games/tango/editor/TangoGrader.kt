package com.ivandsky.puzzlebox.games.tango.editor

import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.engine.TangoHint
import com.ivandsky.puzzlebox.games.tango.engine.TangoRules
import com.ivandsky.puzzlebox.games.tango.engine.TangoState

/**
 * Grades a Tango board by walking [TangoRules.hint] (spec A9): every forced move is placed and
 * its reason maps to a [TangoRung]; the grade is the hardest rung used. The hint ladder tries
 * markers, then three-in-a-row, then line counts, so the walk is deterministic and a board is
 * only as hard as the rungs it cannot avoid. A walk that meets [TangoHint.Stuck] with a cell
 * still empty is [TangoGrade.stuck].
 */
object TangoGrader {
    /** Walks [TangoRules.hint] from the givens; Stuck before full ⇒ stuck. */
    fun grade(puzzle: TangoPuzzle): TangoGrade {
        val grid = MutableList<TangoCell?>(puzzle.cellCount) { puzzle.givens[it] }
        var hardest: TangoRung? = null
        while (grid.any { it == null }) {
            val hint = TangoRules.hint(TangoState(puzzle, grid.toList()))
            if (hint !is TangoHint.Forced) return TangoGrade(hardest, stuck = true)
            grid[hint.idx] = hint.value
            val rung = rungOf(hint.why)
            if (hardest == null || rung > hardest) hardest = rung
        }
        return TangoGrade(hardest, stuck = false)
    }

    private fun rungOf(why: TangoHint.Why): TangoRung = when (why) {
        is TangoHint.Why.MarkerBinding -> TangoRung.MARKER_BINDING
        is TangoHint.Why.ThreeInARow -> TangoRung.THREE_IN_A_ROW
        is TangoHint.Why.LineFull -> TangoRung.LINE_FULL
    }
}
