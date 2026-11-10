package com.ivandsky.puzzlebox.games.strandsui.board

import androidx.compose.ui.geometry.Offset
import com.ivandsky.puzzlebox.gameui.TipKind
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
 *
 * A forward lean ends where the finger is [StrandsBoardSpec.TargetHalf] short of that
 * letter's centre along the step, the near edge of its square target (the corner, for a
 * diagonal): a finger past it that the walk did not join (off the letter's square, or
 * heading elsewhere) has been refused, and a line leant on to the letter would read as a
 * link to a letter outside the path.
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
    val tip = traceTip(
        path,
        fromTail,
        direction,
        neighbour = { cell, dir -> Coord(cell.row + dir.dRow, cell.col + dir.dCol) },
        canExtend = { next ->
            next.row in 0 until rows && next.col in 0 until cols && next !in path && !found(next)
        },
    ) ?: return null
    if (tip.kind != TipKind.Forward) return tip
    return tip.takeIf { it.extent < 1f - StrandsBoardSpec.TargetHalf }
}
