package com.ivandsky.puzzlebox.games.tango.engine.step

import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.engine.TangoCountRule
import com.ivandsky.puzzlebox.games.tango.engine.TangoLineRule
import com.ivandsky.puzzlebox.games.tango.engine.TangoMarkerRule
import com.ivandsky.puzzlebox.games.tango.engine.TangoTripleRule

/**
 * D9: the cell to reveal when no rung fires. Prefers a cell the catalog's what-if closure proves
 * (the opposite value, closed under sign propagation and line options, breaks a rule), nearest
 * the last move; only when the closure proves none does it fall back to the nearest empty cell.
 */
internal object TangoReveal {

    fun pick(puzzle: TangoPuzzle, grid: List<TangoCell?>, lastMove: Int?, solution: List<TangoCell>): TangoStep.Reveal? {
        val empty = (0 until puzzle.cellCount).filter { grid[it] == null }
        val proven = empty.filter { contradicts(puzzle, grid, it, solution[it].other()) }
        val idx = proven.ifEmpty { empty }.minWithOrNull(compareBy({ chebyshev(puzzle.n, it, lastMove) }, { it })) ?: return null
        return TangoStep.Reveal(idx, solution[idx])
    }

    /** Whether [value] on [idx] leads, by forced moves alone, to a broken rule or a cell forced both ways. */
    private fun contradicts(puzzle: TangoPuzzle, grid: List<TangoCell?>, idx: Int, value: TangoCell): Boolean {
        val g = grid.toMutableList().also { it[idx] = value }
        while (true) {
            if (broken(puzzle, g)) return true
            val forced = TangoMarkerRule.forced(puzzle, g) + TangoLineRule.forced(puzzle, g)
            if (forced.isEmpty()) return false
            val next = HashMap<Int, TangoCell>()
            for (f in forced) if (next.getOrPut(f.idx) { f.value } != f.value) return true
            for ((cell, v) in next) g[cell] = v
        }
    }

    private fun broken(puzzle: TangoPuzzle, grid: List<TangoCell?>): Boolean =
        TangoMarkerRule.violation(puzzle, grid) != null ||
            TangoTripleRule.violation(puzzle, grid) != null ||
            TangoCountRule.violation(puzzle, grid) != null

    private fun TangoCell.other(): TangoCell = if (this == TangoCell.SUN) TangoCell.MOON else TangoCell.SUN
}
