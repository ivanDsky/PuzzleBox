package com.ivandsky.puzzlebox.games.strands.engine

import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.StrandsPuzzle

/**
 * The one definition of a traceable path (A2): every cell on the board, each step to one of
 * the 8 neighbours, no cell twice. The trust gate, the rules guard and the trace input all ask here.
 */
object StrandsPath {

    fun isValid(path: List<Coord>, rows: Int, cols: Int): Boolean {
        if (path.any { it.row !in 0 until rows || it.col !in 0 until cols }) return false
        if (path.toSet().size != path.size) return false
        return path.zipWithNext().all { (a, b) -> a.isAdjacentTo(b) }
    }

    /**
     * Some traceable path on [puzzle]'s board that spells [word], or null if none does. The state keeps
     * a non-theme word's spelling, never the cells it was traced through, so a replay that must show it
     * on the board takes the first path in row-major, then neighbour, order.
     */
    fun spelling(puzzle: StrandsPuzzle, word: String): List<Coord>? {
        if (word.isEmpty()) return null
        val path = ArrayList<Coord>(word.length)
        fun walk(at: Coord): Boolean {
            if (at.row !in 0 until puzzle.rows || at.col !in 0 until puzzle.cols) return false
            if (puzzle.letterAt(at) != word[path.size] || at in path) return false
            path += at
            if (path.size == word.length) return true
            for (dr in -1..1) for (dc in -1..1) {
                if ((dr != 0 || dc != 0) && walk(Coord(at.row + dr, at.col + dc))) return true
            }
            path.removeAt(path.lastIndex)
            return false
        }
        for (row in 0 until puzzle.rows) for (col in 0 until puzzle.cols) if (walk(Coord(row, col))) return path
        return null
    }
}
