package com.ivandsky.puzzlebox.games.zip.engine

import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.ZipBoard

/** I5: waypoints must be entered in label order (1, 2, 3, ...). */
class WaypointOrderRule : ZipMoveRule {
    override val id = ZipRuleId.WaypointOrder

    override fun violated(move: ZipMove, state: ZipState): Boolean {
        if (move !is ZipMove.Extend) return false
        val label = state.puzzle.board.waypointLabel(move.to) ?: return false
        return label != expectedLabel(state)
    }
}

/** The label the NEXT waypoint entered must carry: waypoints seen so far, plus one. */
internal fun expectedLabel(state: ZipState): Int =
    state.path.count(state.puzzle.board::isWaypoint) + 1

/** The cell whose label the player should reach next — names [expectedLabel] for the UI. */
internal fun expectedWaypoint(board: ZipBoard, path: List<Cell>): Cell =
    board.waypoints[path.count(board::isWaypoint)]
