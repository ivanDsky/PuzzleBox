package com.ivandsky.puzzlebox.gameui

import androidx.compose.ui.geometry.Offset
import com.ivandsky.puzzlebox.designsystem.MotionTokens

/** The eight steps a trace can take from a cell, in clockwise screen order from +x (y grows down). */
enum class TipDirection(val dRow: Int, val dCol: Int) {
    East(0, 1), SouthEast(1, 1), South(1, 0), SouthWest(1, -1),
    West(0, -1), NorthWest(-1, -1), North(-1, 0), NorthEast(-1, 1),
    ;

    /**
     * How far toward this direction [offset] (cell units from the tail centre) leans, as a
     * fraction of the step to the neighbour, in [0, [MotionTokens.Trace.TipMaxCells]]. Only
     * the component ON the direction's axis counts — a perpendicular finger moves nothing —
     * and it never reaches the neighbour's centre: the cell commits first.
     */
    fun extentOf(offset: Offset): Float {
        val push = (offset.x * dCol + offset.y * dRow) / (dRow * dRow + dCol * dCol).toFloat()
        return push.coerceIn(0f, MotionTokens.Trace.TipMaxCells)
    }
}
