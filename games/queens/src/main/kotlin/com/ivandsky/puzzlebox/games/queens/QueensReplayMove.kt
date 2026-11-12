package com.ivandsky.puzzlebox.games.queens

import com.ivandsky.puzzlebox.game.replay.ReplayMove

/**
 * One recorded Queens action: what happened on the board, not what was pressed, so a replay
 * needs no tap cycle, hint engine or solver to apply it.
 */
sealed interface QueensReplayMove {
    data class Place(val cell: Cell) : QueensReplayMove
    data class Remove(val cell: Cell) : QueensReplayMove
    data class Cross(val cell: Cell) : QueensReplayMove

    /** [extend] joins the previous erase as one undo unit: the erase swipe. */
    data class Erase(val cell: Cell, val extend: Boolean) : QueensReplayMove
    data object Clear : QueensReplayMove
    data object Undo : QueensReplayMove
    data object Redo : QueensReplayMove

    /** One counted hint. A Queens hint changes no cell, so the count is its whole outcome. */
    data object Hint : QueensReplayMove

    /** The Auto-cross setting: a replay must derive the system marks the player saw. */
    data class AutoCross(val on: Boolean) : QueensReplayMove

    fun toReplayMove(): ReplayMove = when (this) {
        is Place -> ReplayMove(PLACE, intArrayOf(cell.idx))
        is Remove -> ReplayMove(REMOVE, intArrayOf(cell.idx))
        is Cross -> ReplayMove(CROSS, intArrayOf(cell.idx))
        is Erase -> ReplayMove(ERASE, intArrayOf(cell.idx, extend.toArg()))
        Clear -> ReplayMove(CLEAR, intArrayOf())
        Undo -> ReplayMove(UNDO, intArrayOf())
        Redo -> ReplayMove(REDO, intArrayOf())
        Hint -> ReplayMove(HINT, intArrayOf())
        is AutoCross -> ReplayMove(AUTO_CROSS, intArrayOf(on.toArg()))
    }

    companion object {
        /** Null when [move] is not a Queens move on a board of [cellCount] cells. */
        fun of(move: ReplayMove, cellCount: Int): QueensReplayMove? {
            val args = move.args
            return when (move.kind) {
                PLACE -> args.singleOrNull()?.toCell(cellCount)?.let(::Place)
                REMOVE -> args.singleOrNull()?.toCell(cellCount)?.let(::Remove)
                CROSS -> args.singleOrNull()?.toCell(cellCount)?.let(::Cross)
                ERASE -> erase(args, cellCount)
                CLEAR -> Clear.takeIf { args.isEmpty() }
                UNDO -> Undo.takeIf { args.isEmpty() }
                REDO -> Redo.takeIf { args.isEmpty() }
                HINT -> Hint.takeIf { args.isEmpty() }
                AUTO_CROSS -> args.singleOrNull()?.toFlag()?.let(::AutoCross)
                else -> null
            }
        }
    }
}

// Persisted in every stored replay: never renumber, only append.
private const val PLACE = 0
private const val REMOVE = 1
private const val CROSS = 2
private const val ERASE = 3
private const val CLEAR = 4
private const val UNDO = 5
private const val REDO = 6
private const val HINT = 7
private const val AUTO_CROSS = 8

private fun erase(args: IntArray, cellCount: Int): QueensReplayMove.Erase? {
    if (args.size != 2) return null
    val cell = args[0].toCell(cellCount) ?: return null
    val extend = args[1].toFlag() ?: return null
    return QueensReplayMove.Erase(cell, extend)
}

private fun Int.toCell(cellCount: Int): Cell? = if (this in 0 until cellCount) Cell(this) else null

private fun Int.toFlag(): Boolean? = when (this) {
    0 -> false
    1 -> true
    else -> null
}

private fun Boolean.toArg(): Int = if (this) 1 else 0
