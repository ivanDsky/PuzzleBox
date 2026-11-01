package com.ivandsky.puzzlebox.games.zip.engine

/** I1: consecutive trace cells are 4-adjacent (no diagonals, no jumps). */
class AdjacencyRule : ZipMoveRule {
    override val id = ZipRuleId.Adjacency

    override fun violated(move: ZipMove, state: ZipState): Boolean {
        if (move !is ZipMove.Extend) return false
        val path = state.path
        if (path.isEmpty()) return false
        return !ZipVerifier.isAdjacent(path.last(), move.to, state.puzzle.board.n)
    }
}
