package com.ivandsky.puzzlebox.gameui

/** An interior edge, always named from its upper/left cell. */
data class GridEdge(val cell: GridCell, val side: Side) {
    enum class Side { Right, Bottom }
}
