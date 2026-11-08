package com.ivandsky.puzzlebox.games.tangoui.editor

import com.ivandsky.puzzlebox.gameui.GridEdge

/** What a press is over: a square or an interior edge. */
sealed interface TangoEditorTarget {
    /** The square at row-major index [cell]. */
    data class Square(val cell: Int) : TangoEditorTarget

    /** The interior [edge], named from its upper or left cell. */
    data class Edge(val edge: GridEdge) : TangoEditorTarget
}
