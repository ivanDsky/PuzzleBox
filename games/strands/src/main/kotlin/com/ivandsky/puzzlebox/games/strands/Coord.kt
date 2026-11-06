package com.ivandsky.puzzlebox.games.strands

/** A board cell, 0-based; row 0 is the top row. */
data class Coord(val row: Int, val col: Int) {

    /** True for the 8 surrounding cells (A2); a cell is never adjacent to itself. */
    fun isAdjacentTo(other: Coord): Boolean {
        val dr = row - other.row
        val dc = col - other.col
        return (dr != 0 || dc != 0) && dr in -1..1 && dc in -1..1
    }
}
