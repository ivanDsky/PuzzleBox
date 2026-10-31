package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.gameui.DragTraceHooks
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.ZipBoard
import com.ivandsky.puzzlebox.games.zip.engine.ZipRules

/**
 * Zip's gesture predicates (finding F3-③): the rules' I1–I5 local checks re-authored as
 * pure look-ahead over the gesture's working trace, so valid drags feel instant. These
 * are a pre-filter only — the screen still validates every emitted move against the
 * committed path via [ZipRules] (F2 §7); the hooks never mutate state.
 */
class ZipDragHooks(private val board: ZipBoard) : DragTraceHooks {

    override fun canExtend(path: List<GridCell>, next: GridCell): Boolean {
        val cell = cellOf(next)
        if (!board.contains(cell)) return false // I8
        if (path.isEmpty()) return cell == board.waypoints.first() // I4
        val end = cellOf(path.last())
        if (!ZipRules.isAdjacent(end, cell, board.n)) return false // I1
        if (board.blocked(end, cell)) return false // I2
        if (path.any { cellOf(it) == cell }) return false // I3
        val label = board.waypointLabel(cell)
        return label == null || label == path.count { board.isWaypoint(cellOf(it)) } + 1 // I5
    }

    override fun retractIndex(path: List<GridCell>, next: GridCell): Int? =
        path.indexOf(next).takeIf { it >= 0 }

    private fun cellOf(cell: GridCell) = Cell(cell.row * board.n + cell.col)
}
