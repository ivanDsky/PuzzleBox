package com.ivandsky.puzzlebox.games.tango.engine.step

import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.engine.TangoLines

/**
 * D4 equal pair: an `=` joins two empty cells of a line and a known cell sits right beside the
 * pair. Matching that cell would make three equal, so both cells take the other value.
 */
object TangoEqualPairRule : TangoStepRule {

    /** Rows first then columns; per pair the cell before it is read before the cell after it. */
    override fun forced(puzzle: TangoPuzzle, grid: List<TangoCell?>): List<TangoStep.Deduction> {
        val n = puzzle.n
        val found = mutableListOf<TangoStep.Deduction>()
        for (line in 0 until 2 * n) {
            for (j in 0 until n - 1) {
                val a = TangoLines.cellAt(n, line, j)
                val b = TangoLines.cellAt(n, line, j + 1)
                if (grid[a] != null || grid[b] != null || puzzle.markerBetween(a, b) != TangoMarkerKind.SAME) continue
                if (j > 0) pinBy(TangoLines.cellAt(n, line, j - 1), a, b, grid, found)
                if (j + 2 < n) pinBy(TangoLines.cellAt(n, line, j + 2), a, b, grid, found)
            }
        }
        return found
    }

    /** Both pair cells take the value opposite the known cell at [beside], if it is known. */
    private fun pinBy(beside: Int, a: Int, b: Int, grid: List<TangoCell?>, found: MutableList<TangoStep.Deduction>) {
        val value = grid[beside]?.other ?: return
        val evidence = setOf(beside, a, b)
        found += TangoStep.Deduction(TangoStepRung.EQUAL_PAIR, a, value, evidence, row = null, col = null, options = null)
        found += TangoStep.Deduction(TangoStepRung.EQUAL_PAIR, b, value, evidence, row = null, col = null, options = null)
    }
}
