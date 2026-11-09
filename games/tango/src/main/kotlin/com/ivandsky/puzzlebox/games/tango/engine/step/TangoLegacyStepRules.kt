package com.ivandsky.puzzlebox.games.tango.engine.step

import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.engine.TangoCountRule
import com.ivandsky.puzzlebox.games.tango.engine.TangoHint
import com.ivandsky.puzzlebox.games.tango.engine.TangoLineRule
import com.ivandsky.puzzlebox.games.tango.engine.TangoMarkerRule
import com.ivandsky.puzzlebox.games.tango.engine.TangoTripleRule

/**
 * The four legacy ladder rules as step rules. The legacy `ThreeInARow` covers two catalog rungs,
 * so it is split: PAIR when the target lies beyond an end of the equal pair (`XX_`, `_XX`),
 * SANDWICH when it lies between the two equal cells (`X_X`).
 */
object TangoLegacyStepRules {

    val sign = TangoStepRule { puzzle, grid -> TangoMarkerRule.forced(puzzle, grid).map { deduction(puzzle, grid, it) } }

    val pair = TangoStepRule { puzzle, grid ->
        TangoTripleRule.forced(puzzle, grid).map { deduction(puzzle, grid, it) }.filter { it.rung == TangoStepRung.PAIR }
    }

    val sandwich = TangoStepRule { puzzle, grid ->
        TangoTripleRule.forced(puzzle, grid).map { deduction(puzzle, grid, it) }.filter { it.rung == TangoStepRung.SANDWICH }
    }

    val fullLine = TangoStepRule { puzzle, grid -> TangoCountRule.forced(puzzle, grid).map { deduction(puzzle, grid, it) } }

    val lineOptions = TangoStepRule { puzzle, grid -> TangoLineRule.forced(puzzle, grid).map { deduction(puzzle, grid, it) } }

    /**
     * Evidence: both cells of the marker (SIGN), the equal pair (PAIR, SANDWICH), the line's cells
     * holding the full value (FULL_LINE), the line's filled cells (LINE_OPTIONS).
     */
    fun deduction(puzzle: TangoPuzzle, grid: List<TangoCell?>, forced: TangoHint.Forced): TangoStep.Deduction {
        val (idx, value) = forced
        return when (val why = forced.why) {
            is TangoHint.Why.MarkerBinding ->
                TangoStep.Deduction(TangoStepRung.SIGN, idx, value, setOf(why.markerCell, why.neighborIdx), null, null, null)
            is TangoHint.Why.ThreeInARow -> {
                val between = idx in minOf(why.first, why.second)..maxOf(why.first, why.second)
                val rung = if (between) TangoStepRung.SANDWICH else TangoStepRung.PAIR
                TangoStep.Deduction(rung, idx, value, setOf(why.first, why.second), null, null, null)
            }
            is TangoHint.Why.LineFull -> {
                val evidence = lineCells(puzzle.n, why.row, why.col).filterTo(LinkedHashSet()) { grid[it] == why.fullOf }
                TangoStep.Deduction(TangoStepRung.FULL_LINE, idx, value, evidence, why.row, why.col, null)
            }
            is TangoHint.Why.LineFills -> {
                val evidence = lineCells(puzzle.n, why.row, why.col).filterTo(LinkedHashSet()) { grid[it] != null }
                TangoStep.Deduction(TangoStepRung.LINE_OPTIONS, idx, value, evidence, why.row, why.col, why.fills)
            }
        }
    }

    /** Row-major cells of row [row] when non-null, else of column [col]. */
    private fun lineCells(n: Int, row: Int?, col: Int?): List<Int> =
        if (row != null) List(n) { row * n + it } else List(n) { it * n + requireNotNull(col) }
}
