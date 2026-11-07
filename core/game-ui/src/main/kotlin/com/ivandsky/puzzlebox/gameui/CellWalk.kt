package com.ivandsky.puzzlebox.gameui

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.floor

/** One cell boundary crossing along a segment walked by [GridGeometry.walkCells]. */
internal sealed interface WalkStep {
    data class Enter(val cell: GridCell) : WalkStep

    /**
     * The segment passes exactly through a grid vertex into [diagonal]. A 4-way trace has
     * to route through a side cell; [first] is the side the segment favours.
     */
    data class Corner(val first: GridCell, val second: GridCell, val diagonal: GridCell) : WalkStep
}

/**
 * Cells crossed by the straight segment [from]→[to], in order, excluding the cell [from]
 * lies in (Amanatides–Woo grid traversal). Points off the board clamp to it, like [GridGeometry.cellAt].
 */
internal fun GridGeometry.walkCells(from: Offset, to: Offset): List<WalkStep> {
    val x0 = (from.x / cellWidth).clampTo(cols)
    val y0 = (from.y / cellHeight).clampTo(rows)
    val x1 = (to.x / cellWidth).clampTo(cols)
    val y1 = (to.y / cellHeight).clampTo(rows)
    var col = floor(x0).toInt()
    var row = floor(y0).toInt()
    val endCol = floor(x1).toInt()
    val endRow = floor(y1).toInt()
    val dx = x1 - x0
    val dy = y1 - y0
    val stepCol = if (dx > 0) 1 else -1
    val stepRow = if (dy > 0) 1 else -1
    var tMaxX = boundaryT(x0, col, dx)
    var tMaxY = boundaryT(y0, row, dy)
    val tDeltaX = if (dx == 0f) Float.POSITIVE_INFINITY else 1f / abs(dx)
    val tDeltaY = if (dy == 0f) Float.POSITIVE_INFINITY else 1f / abs(dy)

    val steps = mutableListOf<WalkStep>()
    // Bounded by the board's Manhattan span and the segment's end, so float drift can
    // neither loop forever nor step past the end cell.
    repeat(rows + cols) {
        if (col == endCol && row == endRow || minOf(tMaxX, tMaxY) > 1f) return steps
        when {
            abs(tMaxX - tMaxY) < VERTEX_EPSILON -> {
                val horizontal = GridCell(row, col + stepCol)
                val vertical = GridCell(row + stepRow, col)
                col += stepCol
                row += stepRow
                val diagonal = GridCell(row, col)
                steps += if (abs(dx) >= abs(dy)) {
                    WalkStep.Corner(horizontal, vertical, diagonal)
                } else {
                    WalkStep.Corner(vertical, horizontal, diagonal)
                }
                tMaxX += tDeltaX
                tMaxY += tDeltaY
            }
            tMaxX < tMaxY -> {
                col += stepCol
                tMaxX += tDeltaX
                steps += WalkStep.Enter(GridCell(row, col))
            }
            else -> {
                row += stepRow
                tMaxY += tDeltaY
                steps += WalkStep.Enter(GridCell(row, col))
            }
        }
    }
    return steps
}

private const val VERTEX_EPSILON = 1e-4f
private const val EDGE_INSET = 1e-3f

private fun Float.clampTo(count: Int): Float = coerceIn(0f, count - EDGE_INSET)

private fun boundaryT(start: Float, cell: Int, delta: Float): Float = when {
    delta > 0 -> (cell + 1 - start) / delta
    delta < 0 -> (start - cell) / -delta
    else -> Float.POSITIVE_INFINITY
}
