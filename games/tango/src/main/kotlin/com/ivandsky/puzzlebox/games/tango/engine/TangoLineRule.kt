package com.ivandsky.puzzlebox.games.tango.engine

import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle

/**
 * Line completion: lists every legal fill of one row or column and forces any empty cell on
 * which all fills agree. It combines the count, three-in-a-row and marker rules inside one
 * line, so it settles what no single rung sees. Rows first, then columns, like
 * [TangoCountRule].
 */
object TangoLineRule {

    /** First empty cell of the first line whose every legal fill agrees on it, or null. */
    fun deduction(puzzle: TangoPuzzle, grid: List<TangoCell?>): TangoHint.Forced? =
        forced(puzzle, grid).firstOrNull()

    /** Every empty cell, in every line, on which all legal fills agree; rows first, then columns. */
    fun forced(puzzle: TangoPuzzle, grid: List<TangoCell?>): List<TangoHint.Forced> {
        val n = puzzle.n
        return (0 until n).flatMap { forcedIn(puzzle, grid, it, isRow = true) } +
            (0 until n).flatMap { forcedIn(puzzle, grid, it, isRow = false) }
    }

    private fun forcedIn(
        puzzle: TangoPuzzle,
        grid: List<TangoCell?>,
        index: Int,
        isRow: Boolean,
    ): List<TangoHint.Forced> {
        val n = puzzle.n
        val cells = IntArray(n) { pos -> if (isRow) index * n + pos else pos * n + index }
        if (cells.none { grid[it] == null }) return emptyList()
        val fills = fillsOf(puzzle, cells, grid)
        if (fills.isEmpty()) return emptyList()
        val found = mutableListOf<TangoHint.Forced>()
        for (pos in 0 until n) {
            if (grid[cells[pos]] != null) continue
            val value = fills.first()[pos]
            if (fills.all { it[pos] == value }) {
                found += TangoHint.Forced(
                    idx = cells[pos],
                    value = value,
                    why = TangoHint.Why.LineFills(
                        row = index.takeIf { isRow },
                        col = index.takeIf { !isRow },
                        fills = fills.size,
                    ),
                )
            }
        }
        return found
    }

    /** Every assignment of the line that keeps its filled cells and breaks no rule inside it. */
    private fun fillsOf(
        puzzle: TangoPuzzle,
        cells: IntArray,
        grid: List<TangoCell?>,
    ): List<List<TangoCell>> {
        val half = puzzle.n / 2
        val fills = mutableListOf<List<TangoCell>>()
        val line = arrayOfNulls<TangoCell>(cells.size)

        fun place(pos: Int, suns: Int, moons: Int) {
            if (suns > half || moons > half) return
            if (pos == cells.size) {
                fills += line.map { requireNotNull(it) }
                return
            }
            val fixed = grid[cells[pos]]
            for (value in listOf(TangoCell.SUN, TangoCell.MOON)) {
                if (fixed != null && fixed != value) continue
                if (pos >= 2 && line[pos - 1] == value && line[pos - 2] == value) continue
                val left = line.getOrNull(pos - 1)
                if (pos >= 1 && markerBroken(puzzle, cells[pos - 1], cells[pos], left, value)) continue
                line[pos] = value
                val sun = if (value == TangoCell.SUN) 1 else 0
                place(pos + 1, suns + sun, moons + 1 - sun)
            }
            line[pos] = null
        }
        place(0, 0, 0)
        return fills
    }

    private fun markerBroken(
        puzzle: TangoPuzzle,
        a: Int,
        b: Int,
        left: TangoCell?,
        right: TangoCell,
    ): Boolean =
        when (puzzle.markerBetween(a, b)) {
            TangoMarkerKind.SAME -> left != right
            TangoMarkerKind.OPPOSITE -> left == right
            null -> false
        }
}
