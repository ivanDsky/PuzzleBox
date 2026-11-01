package com.ivandsky.puzzlebox.games.zip.engine

/** I3: no cell twice — the trace is a simple path. */
class CellReuseRule : ZipMoveRule {
    override val id = ZipRuleId.CellReuse

    override fun violated(move: ZipMove, state: ZipState): Boolean =
        move is ZipMove.Extend && move.to in state.path
}
