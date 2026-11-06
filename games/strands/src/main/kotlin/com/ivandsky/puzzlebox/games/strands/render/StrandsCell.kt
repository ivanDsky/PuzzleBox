package com.ivandsky.puzzlebox.games.strands.render

import com.ivandsky.puzzlebox.games.strands.Coord

/** One projected cell. [traceIndex] is its position in the live trace, drawn over [state]. */
data class StrandsCell(
    val coord: Coord,
    val letter: Char,
    val state: CellState,
    val traceIndex: Int? = null,
)
