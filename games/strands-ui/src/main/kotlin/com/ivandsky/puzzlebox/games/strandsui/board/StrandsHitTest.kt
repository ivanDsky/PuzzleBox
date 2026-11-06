package com.ivandsky.puzzlebox.games.strandsui.board

import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.gameui.GridGeometry
import kotlin.math.hypot

/**
 * Board-local positions to cells over the shared [GridGeometry]. A touch-down takes the
 * whole cell, so the target is the full pitch (SC11); a drag takes a cell only inside its
 * inner circle (A12), so a diagonal stroke passing a corner never catches the neighbour.
 * Unlike [GridGeometry.cellAt], nothing clamps: off the board is null.
 */
internal object StrandsHitTest {

    fun cellAt(geometry: GridGeometry, x: Float, y: Float): Coord? {
        if (x < 0f || y < 0f) return null
        val col = (x / geometry.cellWidth).toInt()
        val row = (y / geometry.cellHeight).toInt()
        if (!geometry.isValid(GridCell(row, col))) return null
        return Coord(row, col)
    }

    fun innerCellAt(geometry: GridGeometry, x: Float, y: Float): Coord? {
        val cell = cellAt(geometry, x, y) ?: return null
        val center = geometry.centerOf(GridCell(cell.row, cell.col))
        val radius = StrandsBoardSpec.HitCircleFraction * minOf(geometry.cellWidth, geometry.cellHeight)
        return cell.takeIf { hypot(x - center.x, y - center.y) <= radius }
    }
}
