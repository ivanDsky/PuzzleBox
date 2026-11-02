package com.ivandsky.puzzlebox.gameui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

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
}
