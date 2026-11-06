package com.ivandsky.puzzlebox.games.queens.editor

import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import kotlin.math.abs

/**
 * Where a no-solution Queens board breaks (spec A8 clash rule). Two structural clashes are
 * located, in this order, and the first one found is returned:
 * - a contiguous band of `k` rows, then of `k` columns (narrowest band first, top/left first),
 *   that wholly contains more regions than the `k * queensPerUnit` queens it can hold — the
 *   cells of those regions;
 * - two 1-cell regions that touch, diagonals included — those two cells.
 *
 * A board can have no solution without either clash; then the result is empty and the
 * verdict reads "No solution" with no cells to blame.
 */
object QueensClashLocator {
    /** Cells of a contiguous row/column band holding more whole regions than its width, or two touching 1-cell regions. */
    fun conflicts(puzzle: QueensPuzzle): Set<Int> {
        val n = puzzle.size
        val cellsOf = (0 until n).map { region -> puzzle.regions.indices.filter { puzzle.regions[it] == region } }
            .filter { it.isNotEmpty() }
        return bandClash(n, puzzle.queensPerUnit, cellsOf, row = { it / n })
            ?: bandClash(n, puzzle.queensPerUnit, cellsOf, row = { it % n })
            ?: touchingSingles(n, cellsOf)
            ?: emptySet()
    }

    /** The first band along the axis [row] maps a cell to that wholly holds too many regions. */
    private fun bandClash(n: Int, perUnit: Int, cellsOf: List<List<Int>>, row: (Int) -> Int): Set<Int>? {
        val spans = cellsOf.map { cells -> cells.minOf(row)..cells.maxOf(row) }
        for (width in 1 until n) {
            for (start in 0..n - width) {
                val band = start until start + width
                val inside = cellsOf.indices.filter { spans[it].first in band && spans[it].last in band }
                if (inside.size > width * perUnit) return inside.flatMapTo(mutableSetOf()) { cellsOf[it] }
            }
        }
        return null
    }

    private fun touchingSingles(n: Int, cellsOf: List<List<Int>>): Set<Int>? {
        val singles = cellsOf.filter { it.size == 1 }.map { it.single() }
        for (i in singles.indices) {
            for (j in i + 1 until singles.size) {
                val a = singles[i]
                val b = singles[j]
                if (abs(a / n - b / n) <= 1 && abs(a % n - b % n) <= 1) return setOf(a, b)
            }
        }
        return null
    }
}
