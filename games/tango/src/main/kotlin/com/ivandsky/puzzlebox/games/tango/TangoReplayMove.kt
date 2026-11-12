package com.ivandsky.puzzlebox.games.tango

import com.ivandsky.puzzlebox.game.replay.ReplayMove

/**
 * One recorded Tango action: what landed on the board, not what was pressed, so a replay
 * needs no tap cycle, hint engine or solver to apply it.
 */
sealed interface TangoReplayMove {
    /** Cell [cell] takes [value]; null empties it. */
    data class Place(val cell: Int, val value: TangoCell?) : TangoReplayMove
    data object Clear : TangoReplayMove
    data object Undo : TangoReplayMove
    data object Redo : TangoReplayMove

    /** One counted hint. A hint that places a cell is recorded as that [Place] and then this, at one stamp. */
    data object Hint : TangoReplayMove

    /** A mistake live play counted right after the [Place] that broke a rule. */
    data object Rejection : TangoReplayMove

    fun toReplayMove(): ReplayMove = when (this) {
        is Place -> ReplayMove(PLACE, intArrayOf(cell, value?.toByte() ?: EMPTY))
        Clear -> ReplayMove(CLEAR, intArrayOf())
        Undo -> ReplayMove(UNDO, intArrayOf())
        Redo -> ReplayMove(REDO, intArrayOf())
        Hint -> ReplayMove(HINT, intArrayOf())
        Rejection -> ReplayMove(REJECTION, intArrayOf())
    }

    companion object {
        /** Null when [move] is not a Tango move on a board of [cellCount] cells. */
        fun of(move: ReplayMove, cellCount: Int): TangoReplayMove? {
            val args = move.args
            return when (move.kind) {
                PLACE -> place(args, cellCount)
                CLEAR -> Clear.takeIf { args.isEmpty() }
                UNDO -> Undo.takeIf { args.isEmpty() }
                REDO -> Redo.takeIf { args.isEmpty() }
                HINT -> Hint.takeIf { args.isEmpty() }
                REJECTION -> Rejection.takeIf { args.isEmpty() }
                else -> null
            }
        }
    }
}

// Persisted in every stored replay: never renumber, only append.
private const val PLACE = 0
private const val CLEAR = 1
private const val UNDO = 2
private const val REDO = 3
private const val HINT = 4
private const val REJECTION = 5

private const val EMPTY = 0

private fun place(args: IntArray, cellCount: Int): TangoReplayMove.Place? {
    if (args.size != 2 || args[0] !in 0 until cellCount) return null
    if (args[1] == EMPTY) return TangoReplayMove.Place(args[0], null)
    val value = TangoCell.fromByte(args[1]) ?: return null
    return TangoReplayMove.Place(args[0], value)
}
