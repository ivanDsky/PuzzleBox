package com.ivandsky.puzzlebox.games.strands.engine

import com.ivandsky.puzzlebox.games.strands.Coord

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
}
