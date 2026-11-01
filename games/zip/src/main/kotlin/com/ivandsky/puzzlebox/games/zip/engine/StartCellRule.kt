package com.ivandsky.puzzlebox.games.zip.engine

/** I4: the empty trace may only start on waypoint 1. Silent once the trace exists. */
class StartCellRule : ZipMoveRule {
    override val id = ZipRuleId.StartCell

    override fun violated(move: ZipMove, state: ZipState): Boolean =
        move is ZipMove.Extend &&
            state.path.isEmpty() &&
            move.to != state.puzzle.board.waypoints.first()
}
