package com.ivandsky.puzzlebox.games.tango.engine.step

import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.engine.TangoLineRule
import com.ivandsky.puzzlebox.games.tango.engine.TangoLines
import com.ivandsky.puzzlebox.games.tango.engine.TangoMarkerRule

/**
 * D9: the cell to reveal when no rung fires. Walks the empty cells nearest the last move first
 * (then lowest index) and names the first one the catalog's what-if closure proves (the opposite
 * value, closed under sign propagation and line options, contradicts); only when it proves none
 * does it fall back to the nearest empty cell.
 */
internal object TangoReveal {

    fun pick(puzzle: TangoPuzzle, grid: List<TangoCell?>, lastMove: Int?, solution: List<TangoCell>): TangoStep.Reveal? {
        val n = puzzle.n
        val empties = (0 until puzzle.cellCount).filter { grid[it] == null }
            .sortedWith(compareBy({ chebyshev(n, it, lastMove) }, { it }))
        if (empties.isEmpty()) return null
        val lines = TangoLines.all(n)
        val scratch = grid.toMutableList()
        val idx = empties.firstOrNull { idx ->
            for (cell in scratch.indices) scratch[cell] = grid[cell]
            scratch[idx] = solution[idx].other
            contradicts(puzzle, lines, scratch)
        } ?: empties.first()
        return TangoStep.Reveal(idx, solution[idx])
    }

    /**
     * Closes [g] in place under sign propagation and line options until nothing changes (lab
     * `closure`): a broken sign or a line with no legal fill is a contradiction, and so is a
     * broken sign once the closure settles.
     */
    private fun contradicts(puzzle: TangoPuzzle, lines: Array<IntArray>, g: MutableList<TangoCell?>): Boolean {
        val n = puzzle.n
        val markers = puzzle.markers
        var changed = true
        while (changed) {
            changed = false
            for (m in markers.indices) {
                val marker = markers[m]
                val a = marker.cell
                val b = if (marker.side == TangoEdgeSide.RIGHT) a + 1 else a + n
                val same = marker.kind == TangoMarkerKind.SAME
                val va = g[a]
                val vb = g[b]
                if (va != null && vb != null) {
                    if ((va == vb) != same) return true
                } else if (va != null) {
                    g[b] = if (same) va else va.other
                    changed = true
                } else if (vb != null) {
                    g[a] = if (same) vb else vb.other
                    changed = true
                }
            }
            for (cells in lines) {
                val fills = TangoLineRule.fillsOf(puzzle, cells, g)
                if (fills.isEmpty()) return true
                for (pos in cells.indices) {
                    if (g[cells[pos]] != null) continue
                    val value = fills[0][pos]
                    if (fills.all { it[pos] == value }) {
                        g[cells[pos]] = value
                        changed = true
                    }
                }
            }
        }
        return TangoMarkerRule.violation(puzzle, g) != null
    }
}
