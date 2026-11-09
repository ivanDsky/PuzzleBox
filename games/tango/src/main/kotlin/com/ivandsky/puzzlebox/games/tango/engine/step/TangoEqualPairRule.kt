package com.ivandsky.puzzlebox.games.tango.engine.step

import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle

/**
 * D4 equal pair: an `=` joins two empty cells of a line and a known cell sits right beside the
 * pair. Matching that cell would make three equal, so both cells take the other value.
 */
object TangoEqualPairRule : TangoStepRule {

    /** Rows first then columns; per pair the cell before it is read before the cell after it. */
    override fun forced(puzzle: TangoPuzzle, grid: List<TangoCell?>): List<TangoStep.Deduction> {
        val n = puzzle.n
        val found = mutableListOf<TangoStep.Deduction>()
        for (line in lines(n)) {
            for (j in 0 until n - 1) {
                val a = line[j]
                val b = line[j + 1]
                if (grid[a] != null || grid[b] != null || puzzle.markerBetween(a, b) != TangoMarkerKind.SAME) continue
                for (side in listOf(j - 1, j + 2)) {
                    val beside = line.getOrNull(side) ?: continue
                    val known = grid[beside] ?: continue
                    val evidence = setOf(beside, a, b)
                    found += deduction(a, known.other(), evidence)
                    found += deduction(b, known.other(), evidence)
                }
            }
        }
        return found
    }

    private fun deduction(idx: Int, value: TangoCell, evidence: Set<Int>) =
        TangoStep.Deduction(TangoStepRung.EQUAL_PAIR, idx, value, evidence, row = null, col = null, options = null)

    /** Every row then every column of an n×n board, as row-major cell indices. */
    private fun lines(n: Int): List<List<Int>> =
        (0 until n).map { r -> (0 until n).map { c -> r * n + c } } + (0 until n).map { c -> (0 until n).map { r -> r * n + c } }

    private fun TangoCell.other(): TangoCell = if (this == TangoCell.SUN) TangoCell.MOON else TangoCell.SUN
}
