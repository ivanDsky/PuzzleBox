package com.ivandsky.puzzlebox.games.zip.engine

/**
 * I6 (the A16 fix, now a rule): a FULL trace must END at the final waypoint — a walk that
 * enters waypoint k mid-path and finishes elsewhere is a dead trace, never a solution
 * (ZipRules.isComplete and ZipSearch's solution gate agree).
 */
class EndAtLastWaypointRule : ZipCompletionRule {
    override val id = ZipRuleId.EndAtLastWaypoint

    override fun violated(move: ZipMove, state: ZipState): Boolean {
        val path = state.path
        if (path.size != state.puzzle.board.cellCount) return false // vacuous mid-game
        return path.last() != state.puzzle.board.waypoints.last()
    }
}
