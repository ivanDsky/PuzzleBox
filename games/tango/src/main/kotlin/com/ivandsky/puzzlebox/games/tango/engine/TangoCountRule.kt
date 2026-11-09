package com.ivandsky.puzzlebox.games.tango.engine

import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle

/**
 * Constraint class 2: equal suns and moons per row and column — n/2 each (A3; even N implied).
 * Self-contained unit — [TangoRules] dispatches to it for validation and count-completion.
 */
object TangoCountRule {

    /** Row-major index of the offending (over-half) cell in the first violating line, or null. */
    fun violation(puzzle: TangoPuzzle, grid: List<TangoCell?>): Int? {
        val half = puzzle.n / 2
        for (line in lines(puzzle.n)) {
            val cells = (0 until line.size).map(line)
            for (value in listOf(TangoCell.SUN, TangoCell.MOON)) {
                if (cells.count { grid[it] == value } > half) return cells.last { grid[it] == value }
            }
        }
        return null
    }

    /** First line already holding n/2 of one value with an empty cell left — the gap takes the other value. */
    fun deduction(puzzle: TangoPuzzle, grid: List<TangoCell?>): TangoHint.Forced? =
        forced(puzzle, grid).firstOrNull()

    /** Every empty cell of every line already holding n/2 of one value, in scan order. */
    fun forced(puzzle: TangoPuzzle, grid: List<TangoCell?>): List<TangoHint.Forced> {
        val half = puzzle.n / 2
        val found = mutableListOf<TangoHint.Forced>()
        for (line in lines(puzzle.n)) {
            val cells = (0 until line.size).map(line)
            for (value in listOf(TangoCell.SUN, TangoCell.MOON)) {
                if (cells.count { grid[it] == value } != half) continue
                val forced = if (value == TangoCell.SUN) TangoCell.MOON else TangoCell.SUN
                for (empty in cells.filter { grid[it] == null }) {
                    found += TangoHint.Forced(
                        idx = empty,
                        value = forced,
                        why = TangoHint.Why.LineFull(
                            row = line.index.takeIf { line.isRow },
                            col = line.index.takeIf { !line.isRow },
                            fullOf = value,
                        ),
                    )
                }
            }
        }
        return found
    }

    /** One row or column: position 0 until size maps to a row-major cell index. */
    private data class Line(val n: Int, val index: Int, val isRow: Boolean) : (Int) -> Int {
        override fun invoke(pos: Int): Int = if (isRow) index * n + pos else pos * n + index
        val size: Int get() = n
    }

    /** Rows (first) then columns of an n×n board. */
    private fun lines(n: Int): List<Line> =
        (0 until n).map { r -> Line(n, r, isRow = true) } + (0 until n).map { c -> Line(n, c, isRow = false) }
}
