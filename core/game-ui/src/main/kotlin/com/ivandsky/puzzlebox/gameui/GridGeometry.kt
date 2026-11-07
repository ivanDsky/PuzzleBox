package com.ivandsky.puzzlebox.gameui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * A board cell in explicit rows x cols coordinates (finding F3-②: the board API is never
 * assumed square — Strands plays 6x8). Row 0 is the top, col 0 the left.
 */
data class GridCell(val row: Int, val col: Int) {
    override fun toString(): String = "($row,$col)"
}

/**
 * Pure rows x cols cell geometry shared by board drawing and drag hit-testing — the math
 * stays testable without a device. Drags that leave the board clamp to the boundary cell
 * so a gesture exiting the edge still lands on a valid cell (designsystem precedent).
 */
class GridGeometry(
    val rows: Int,
    val cols: Int,
    widthPx: Float,
    heightPx: Float,
) {
    init {
        require(rows > 0 && cols > 0) { "board must have rows > 0 and cols > 0, got ${rows}x$cols" }
    }

    val cellWidth: Float = widthPx / cols
    val cellHeight: Float = heightPx / rows

    fun cellAt(x: Float, y: Float): GridCell = GridCell(
        row = (y / cellHeight).toInt().coerceIn(0, rows - 1),
        col = (x / cellWidth).toInt().coerceIn(0, cols - 1),
    )

    fun cellAt(offset: Offset): GridCell = cellAt(offset.x, offset.y)

    fun cellRect(cell: GridCell): Rect = Rect(
        left = cell.col * cellWidth,
        top = cell.row * cellHeight,
        right = (cell.col + 1) * cellWidth,
        bottom = (cell.row + 1) * cellHeight,
    )

    fun centerOf(cell: GridCell): Offset = Offset(
        x = (cell.col + 0.5f) * cellWidth,
        y = (cell.row + 0.5f) * cellHeight,
    )

    fun isValid(cell: GridCell): Boolean =
        cell.row in 0 until rows && cell.col in 0 until cols

    /**
     * The interior edge under [position], or null. [band] is the hit half-width across the edge
     * and [inset] the dead length at each end, both as fractions of the cell size. Outer borders
     * and the corner overlap of two bands return null, so the tap falls through to the cell.
     */
    fun edgeAt(position: Offset, band: Float = 0.22f, inset: Float = 0.22f): GridEdge? {
        val fx = position.x / cellWidth
        val fy = position.y / cellHeight
        if (fx < 0f || fy < 0f || fx >= cols || fy >= rows) return null
        val line = fx.roundToInt()
        val lineRow = fy.roundToInt()
        val nearVertical = line in 1 until cols && abs(fx - line) <= band
        val nearHorizontal = lineRow in 1 until rows && abs(fy - lineRow) <= band
        return when {
            nearVertical && nearHorizontal -> null
            nearVertical && alongIsLive(fy, inset) ->
                GridEdge(GridCell(fy.toInt(), line - 1), GridEdge.Side.Right)
            nearHorizontal && alongIsLive(fx, inset) ->
                GridEdge(GridCell(lineRow - 1, fx.toInt()), GridEdge.Side.Bottom)
            else -> null
        }
    }

    private fun alongIsLive(fraction: Float, inset: Float): Boolean {
        val within = fraction - fraction.toInt()
        return within >= inset && within <= 1f - inset
    }
}
