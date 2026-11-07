package com.ivandsky.puzzlebox.games.strandsui.board

import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.gameui.GridGeometry

/**
 * A touch-down's cell over the shared [GridGeometry]: the whole pitch is its target (SC11);
 * what a drag joins afterwards is [StrandsTraceWalk]'s. Unlike [GridGeometry.cellAt],
 * nothing clamps: off the board is null.
 */
internal object StrandsHitTest {

    fun cellAt(geometry: GridGeometry, x: Float, y: Float): Coord? {
        if (x < 0f || y < 0f) return null
        val col = (x / geometry.cellWidth).toInt()
        val row = (y / geometry.cellHeight).toInt()
        if (!geometry.isValid(GridCell(row, col))) return null
        return Coord(row, col)
    }
}
