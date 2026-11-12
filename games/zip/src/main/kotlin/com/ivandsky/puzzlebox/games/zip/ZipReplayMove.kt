package com.ivandsky.puzzlebox.games.zip

import com.ivandsky.puzzlebox.game.replay.ReplayMove

/**
 * One recorded Zip action: what happened on the trace, not what was pressed, so a replay
 * needs no drag gesture, hint engine or solver to apply it. A drag is one [Extend] or
 * [RetractTo] per cell; a hint is its resolved trim, [Hint], then its step; a refused drop is [Rejection].
 */
sealed interface ZipReplayMove {
    data class Extend(val cell: Cell) : ZipReplayMove
    data class RetractTo(val cell: Cell) : ZipReplayMove
    data object Reset : ZipReplayMove
    data object Undo : ZipReplayMove
    data object Redo : ZipReplayMove

    /** One counted hint. Its trim and step are separate moves, so the count is all it carries. */
    data object Hint : ZipReplayMove

    /** The rules refused a step: live play counts a mistake. The refused [Extend] itself is never recorded. */
    data object Rejection : ZipReplayMove

    fun toReplayMove(): ReplayMove = when (this) {
        is Extend -> ReplayMove(EXTEND, intArrayOf(cell.idx))
        is RetractTo -> ReplayMove(RETRACT_TO, intArrayOf(cell.idx))
        Reset -> ReplayMove(RESET, intArrayOf())
        Undo -> ReplayMove(UNDO, intArrayOf())
        Redo -> ReplayMove(REDO, intArrayOf())
        Hint -> ReplayMove(HINT, intArrayOf())
        Rejection -> ReplayMove(REJECTION, intArrayOf())
    }

    companion object {
        /** Null when [move] is not a Zip move on a board of [cellCount] cells. */
        fun of(move: ReplayMove, cellCount: Int): ZipReplayMove? {
            val args = move.args
            return when (move.kind) {
                EXTEND -> args.singleOrNull()?.toCell(cellCount)?.let(::Extend)
                RETRACT_TO -> args.singleOrNull()?.toCell(cellCount)?.let(::RetractTo)
                RESET -> Reset.takeIf { args.isEmpty() }
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
private const val EXTEND = 0
private const val RETRACT_TO = 1
private const val RESET = 2
private const val UNDO = 3
private const val REDO = 4
private const val HINT = 5
private const val REJECTION = 6

private fun Int.toCell(cellCount: Int): Cell? = if (this in 0 until cellCount) Cell(this) else null
