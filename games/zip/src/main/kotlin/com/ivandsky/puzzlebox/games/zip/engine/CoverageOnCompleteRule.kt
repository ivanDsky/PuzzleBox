package com.ivandsky.puzzlebox.games.zip.engine

/** I7: the finishing trace covers the whole board. A completion judge, not a move gate. */
class CoverageOnCompleteRule : ZipCompletionRule {
    override val id = ZipRuleId.CoverageOnComplete

    override fun violated(move: ZipMove, state: ZipState): Boolean =
        state.path.size != state.puzzle.board.cellCount
}
