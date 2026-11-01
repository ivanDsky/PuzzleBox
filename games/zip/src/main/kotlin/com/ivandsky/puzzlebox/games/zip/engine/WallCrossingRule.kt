package com.ivandsky.puzzlebox.games.zip.engine

/** I2: a step may not cross an interior wall. */
class WallCrossingRule : ZipMoveRule {
    override val id = ZipRuleId.WallCrossing

    override fun violated(move: ZipMove, state: ZipState): Boolean {
        if (move !is ZipMove.Extend) return false
        val path = state.path
        if (path.isEmpty()) return false
        return state.puzzle.board.blocked(path.last(), move.to)
    }
}
