package com.ivandsky.puzzlebox.games.zipui

import androidx.compose.ui.geometry.Offset
import com.ivandsky.puzzlebox.gameui.DragTraceHooks
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.gameui.TraceTip
import com.ivandsky.puzzlebox.gameui.dominantDirection
import com.ivandsky.puzzlebox.gameui.traceTip

/**
 * Zip's tip (spec A4): [finger] in cell units (x = column, y = row) leans the line's end
 * along the finger's dominant axis from the tail of [path] — a diagonal finger names no
 * axis — and only at a cell [hooks] would take as the next one, or back at the previous
 * cell; toward a wall, the edge, a used cell or an out-of-order waypoint, no tip. Null
 * without a finger, an empty path or a finger resting on the tail centre.
 */
internal fun zipTip(path: List<GridCell>, finger: Offset, hooks: DragTraceHooks): TraceTip<GridCell>? {
    val tail = path.lastOrNull() ?: return null
    val fromTail = finger - Offset(tail.col + 0.5f, tail.row + 0.5f)
    val direction = dominantDirection(fromTail, eightWay = false) ?: return null
    return traceTip(
        path,
        fromTail,
        direction,
        neighbour = { cell, dir -> GridCell(cell.row + dir.dRow, cell.col + dir.dCol) },
        canExtend = { hooks.canExtend(path, it) },
    )
}
