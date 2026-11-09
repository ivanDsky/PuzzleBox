package com.ivandsky.puzzlebox.games.tango.engine

import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle

/**
 * Constraint class 1: no three identical cells in any row or column (A3). Self-contained
 * unit — [TangoRules] dispatches to it for validation and three-in-a-row deductions.
 */
object TangoTripleRule {

    /** Row-major index of the third cell of the first run of three identical cells, or null. */
    fun violation(puzzle: TangoPuzzle, grid: List<TangoCell?>): Int? {
        val n = puzzle.n
        for (line in lines(n)) {
            var run = 1
            for (i in 1 until n) {
                val prev = grid[line(i - 1)]
                val cur = grid[line(i)]
                run = if (prev != null && prev == cur) run + 1 else 1
                if (run >= 3) return line(i)
            }
        }
        return null
    }

    /**
     * First pair of identical adjacent filled cells with an empty neighbor in line —
     * the empty cell would make three, so it is forced to the other value.
     */
    fun deduction(puzzle: TangoPuzzle, grid: List<TangoCell?>): TangoHint.Forced? =
        forced(puzzle, grid).firstOrNull()

    /** Every `XX_`, `_XX` or `X_X` window, rows first then columns, in scan order. */
    fun forced(puzzle: TangoPuzzle, grid: List<TangoCell?>): List<TangoHint.Forced> {
        val n = puzzle.n
        val found = mutableListOf<TangoHint.Forced>()
        for (line in lines(n)) {
            for (i in 0 until n - 2) {
                val a = grid[line(i)]
                val b = grid[line(i + 1)]
                val c = grid[line(i + 2)]
                // XX_ : the gap completes the run; _XX : the gap opens it; X_X : the gap fills it.
                if (a != null && a == b && c == null) found += forced(line(i + 2), a.other(), line(i), line(i + 1))
                if (b != null && b == c && a == null) found += forced(line(i), b.other(), line(i + 1), line(i + 2))
                if (a != null && c != null && a == c && b == null) found += forced(line(i + 1), a.other(), line(i), line(i + 2))
            }
        }
        return found
    }

    private fun forced(idx: Int, value: TangoCell, first: Int, second: Int) =
        TangoHint.Forced(idx, value, TangoHint.Why.ThreeInARow(first, second))

    /** All rows (first) then all columns of an n×n board, each as an index function. */
    private fun lines(n: Int): List<(Int) -> Int> =
        (0 until n).map { r -> { c: Int -> r * n + c } } + (0 until n).map { c -> { r: Int -> r * n + c } }

    private fun TangoCell.other(): TangoCell = if (this == TangoCell.SUN) TangoCell.MOON else TangoCell.SUN
}
