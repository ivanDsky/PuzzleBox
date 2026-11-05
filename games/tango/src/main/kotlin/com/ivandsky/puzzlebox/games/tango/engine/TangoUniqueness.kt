package com.ivandsky.puzzlebox.games.tango.engine

import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle

/**
 * A15: every accepted Tango puzzle has exactly one solution — ambiguous puzzles are
 * rejected, never guessed through. Exhaustive solver over the empty cells with local
 * consistency pruning; exposed for the fetch path (ticket 03) to gate dailies and
 * archive playback. JVM-only, pure.
 */
object TangoUniqueness {

    /**
     * True iff [puzzle] has exactly one solution under the three constraint classes.
     * Odd N is rejected outright: the count rule (n/2 per value per line) is then
     * unsatisfiable, so no solution can exist.
     */
    fun isUniquelySolvable(puzzle: TangoPuzzle): Boolean {
        if (puzzle.n % 2 != 0) return false
        return countSolutions(puzzle, limit = 2) == 1
    }

    /** Counts solutions up to [limit] by backtracking over empty cells. */
    fun countSolutions(puzzle: TangoPuzzle, limit: Int = 2): Int = search(puzzle, limit) {}

    /**
     * [puzzle]'s one solution, row-major and fully filled (givens included), by the same
     * search [countSolutions] runs; null when it has none or more than one (A15: an
     * ambiguous puzzle is never guessed through) or an odd size.
     */
    fun solve(puzzle: TangoPuzzle): List<TangoCell>? {
        if (puzzle.n % 2 != 0) return null
        var first: List<TangoCell>? = null
        val count = search(puzzle, limit = 2) { grid -> if (first == null) first = grid.map { it!! } }
        return first.takeIf { count == 1 }
    }

    /** Backtracks over the empty cells, handing each full grid to [onSolution], up to [limit]; returns the count. */
    private fun search(puzzle: TangoPuzzle, limit: Int, onSolution: (Array<TangoCell?>) -> Unit): Int {
        val grid = Array<TangoCell?>(puzzle.cellCount) { idx -> puzzle.givens[idx] }
        val empty = (0 until puzzle.cellCount).filter { idx -> grid[idx] == null }
        var count = 0

        fun consistentAt(idx: Int): Boolean {
            val n = puzzle.n
            // The placed cell's own lines and marker edges — everything a new value can break.
            if (TangoMarkerRule.violationAt(puzzle, grid.toList(), idx)) return false
            if (tripleThrough(grid, n, idx)) return false
            val half = n / 2
            val rowCountS = countLine(grid, n, idx / n * n, 1)
            val colCountS = countLine(grid, n, idx % n, n)
            val rowCountM = countLine(grid, n, idx / n * n, 1, TangoCell.MOON)
            val colCountM = countLine(grid, n, idx % n, n, TangoCell.MOON)
            return rowCountS <= half && colCountS <= half && rowCountM <= half && colCountM <= half
        }

        fun rec(i: Int) {
            if (count >= limit) return
            if (i == empty.size) {
                count++
                onSolution(grid)
                return
            }
            val idx = empty[i]
            for (value in listOf(TangoCell.SUN, TangoCell.MOON)) {
                grid[idx] = value
                if (consistentAt(idx)) rec(i + 1)
            }
            grid[idx] = null
        }
        rec(0)
        return count
    }

    private fun countLine(grid: Array<TangoCell?>, n: Int, start: Int, step: Int, value: TangoCell = TangoCell.SUN): Int {
        var c = 0
        for (k in 0 until n) if (grid[start + k * step] == value) c++
        return c
    }

    private fun tripleThrough(grid: Array<TangoCell?>, n: Int, idx: Int): Boolean {
        val value = grid[idx] ?: return false
        val row = idx / n
        val col = idx % n
        // Every length-3 run (row and column) that contains idx.
        for (shift in -2..0) {
            val c0 = col + shift
            if (c0 >= 0 && c0 + 2 < n && (0..2).all { grid[row * n + c0 + it] == value }) return true
            val r0 = row + shift
            if (r0 >= 0 && r0 + 2 < n && (0..2).all { grid[(r0 + it) * n + col] == value }) return true
        }
        return false
    }
}
