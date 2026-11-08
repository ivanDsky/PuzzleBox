package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.ui.geometry.Offset
import com.ivandsky.puzzlebox.gameui.TraceTip
import com.ivandsky.puzzlebox.gameui.dominantDirection
import com.ivandsky.puzzlebox.gameui.traceTip
import com.ivandsky.puzzlebox.games.strands.Coord

/**
 * Strands' tip (spec A4): [finger] in cell units (x = column, y = row) leans the connector's
 * end along the nearest of the eight compasses from the tail of [path], and only at a letter
 * the board would join next — on the board, not already in the path, not a [found] one — or
 * back at the previous letter; anywhere else, no tip. Null without a finger, an empty path or
 * a finger resting on the tail centre.
 */
internal fun strandsTip(
    path: List<Coord>,
    finger: Offset,
    rows: Int,
    cols: Int,
    found: (cell: Coord) -> Boolean,
): TraceTip<Coord>? {
    val tail = path.lastOrNull() ?: return null
    val fromTail = finger - centreOf(tail)
    val direction = dominantDirection(fromTail, eightWay = true) ?: return null
    return traceTip(
        path,
        fromTail,
        direction,
        neighbour = { cell, dir -> Coord(cell.row + dir.dRow, cell.col + dir.dCol) },
        canExtend = { next ->
            next.row in 0 until rows && next.col in 0 until cols && next !in path && !found(next)
        },
    )
}
