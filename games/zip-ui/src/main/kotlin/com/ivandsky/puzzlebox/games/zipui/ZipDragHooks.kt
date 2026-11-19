package com.ivandsky.puzzlebox.games.zipui

import com.ivandsky.puzzlebox.gameui.DragTraceHooks
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.ZipBoard
import com.ivandsky.puzzlebox.games.zip.engine.ZipRules

/**
 * Zip's gesture predicate (finding F3-③): the rules' I1–I5 local checks re-authored as
 * pure look-ahead over the gesture's working trace, so valid drags feel instant. This is
 * a pre-filter only — the screen still validates every emitted move against the
 * committed path via [ZipRules] (F2 §7); the hook never mutates state. Retraction is
 * gesture-layer law (ticket 26 R1): draw-back retracts by one, tap retracts deliberately,
 * and the hook no longer participates in either.
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

    /**
     * A free cell beside the tail is cut off once it has fewer free neighbours than it needs: a
     * way in and a way out, or only a way in for the last number. [next], the new tail, counts.
     */
    override fun strands(path: List<GridCell>, next: GridCell): Boolean {
        val tail = cellOf(path.last())
        val step = cellOf(next)
        val drawn = BooleanArray(board.cellCount)
        for (cell in path) drawn[cellOf(cell).idx] = true
        drawn[step.idx] = true
        return board.neighbors(tail).any { cell ->
            val needs = if (cell == board.waypoints.last()) 1 else 2
            !drawn[cell.idx] && !board.blocked(tail, cell) &&
                board.neighbors(cell).count { (!drawn[it.idx] || it == step) && !board.blocked(cell, it) } < needs
        }
    }

    private fun cellOf(cell: GridCell) = Cell(cell.row * board.n + cell.col)
}
