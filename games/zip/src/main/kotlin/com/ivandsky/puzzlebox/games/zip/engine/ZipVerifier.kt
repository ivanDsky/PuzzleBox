package com.ivandsky.puzzlebox.games.zip.engine

import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.ZipBoard
import com.ivandsky.puzzlebox.games.zip.ZipPuzzle

/**
 * Linear path verification + completion tables (A18 addendum §3): the A16 oracle's
 * verification half, extracted from the ZipRules companions so the rules facade, the
 * solver, and the fetch gate share one implementation. Stateless math over the board.
 */
object ZipVerifier {

    /** 4-adjacency on an n×n row-major board: left/right within the row, up/down by n. */
    fun isAdjacent(a: Cell, b: Cell, n: Int): Boolean {
        val diff = b.idx - a.idx
        return (diff == 1 && a.idx / n == b.idx / n) ||
            (diff == -1 && a.idx / n == b.idx / n) ||
            diff == n ||
            diff == -n
    }

    /**
     * Completion table: per cell, the 0-based index of its waypoint label, or -1. The
     * search and any order-checking consumer read it as a flat O(1) lookup.
     */
    fun waypointIndexTable(board: ZipBoard): IntArray {
        val table = IntArray(board.cellCount) { -1 }
        board.waypoints.forEachIndexed { i, cell -> table[cell.idx] = i }
        return table
    }

    /**
     * Linear verification that [path] satisfies I1–I5 and I8 against [puzzle] — the
     * A16 solution-verification half and the core of [ZipRules.isComplete]. Also
     * implicitly required of any prefix the hint machinery extends.
     */
    fun verifyPath(puzzle: ZipPuzzle, path: List<Cell>): Boolean {
        val board = puzzle.board
        if (path.isEmpty()) return false
        if (path.first() != board.waypoints.first()) return false // I4
        if (path.size > board.cellCount) return false
        val visited = HashSet<Cell>(path.size * 2)
        var waypointsSeen = 0
        for ((index, cell) in path.withIndex()) {
            if (!board.contains(cell)) return false // I8
            if (!visited.add(cell)) return false // I3
            val label = board.waypointLabel(cell)
            if (label != null) {
                if (label != waypointsSeen + 1) return false // I5
                waypointsSeen++
            }
            if (index > 0) {
                val prev = path[index - 1]
                if (!isAdjacent(prev, cell, board.n)) return false // I1
                if (board.blocked(prev, cell)) return false // I2
            }
        }
        return true
    }
}
