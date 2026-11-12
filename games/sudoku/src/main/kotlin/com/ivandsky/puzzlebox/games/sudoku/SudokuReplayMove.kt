package com.ivandsky.puzzlebox.games.sudoku

import com.ivandsky.puzzlebox.game.replay.ReplayMove

/**
 * One recorded Sudoku action: what happened on the board, not what was pressed, so a replay
 * needs no hint engine or solver to apply it.
 */
sealed interface SudokuReplayMove {
    data class Place(val idx: Int, val digit: Int) : SudokuReplayMove
    data class ToggleNote(val idx: Int, val digit: Int) : SudokuReplayMove
    data class Erase(val idx: Int) : SudokuReplayMove

    /** Wipes every player entry as one undo unit. */
    data object Clear : SudokuReplayMove
    data object Undo : SudokuReplayMove
    data object Redo : SudokuReplayMove

    /** A hint's digit entered as a revealed cell; the digit is the hint's outcome. */
    data class HintReveal(val idx: Int, val digit: Int) : SudokuReplayMove

    /** One counted hint. */
    data object Hint : SudokuReplayMove

    fun toReplayMove(): ReplayMove = when (this) {
        is Place -> ReplayMove(PLACE, intArrayOf(idx, digit))
        is ToggleNote -> ReplayMove(TOGGLE_NOTE, intArrayOf(idx, digit))
        is Erase -> ReplayMove(ERASE, intArrayOf(idx))
        Clear -> ReplayMove(CLEAR, intArrayOf())
        Undo -> ReplayMove(UNDO, intArrayOf())
        Redo -> ReplayMove(REDO, intArrayOf())
        is HintReveal -> ReplayMove(HINT_REVEAL, intArrayOf(idx, digit))
        Hint -> ReplayMove(HINT, intArrayOf())
    }

    companion object {
        /** Null when [move] is not a Sudoku move on [geometry]. */
        fun of(move: ReplayMove, geometry: SudokuGeometry): SudokuReplayMove? {
            val args = move.args
            return when (move.kind) {
                PLACE -> cellDigit(args, geometry)?.let { (idx, digit) -> Place(idx, digit) }
                TOGGLE_NOTE -> cellDigit(args, geometry)?.let { (idx, digit) -> ToggleNote(idx, digit) }
                ERASE -> args.singleOrNull()?.takeIf { it in 0 until geometry.cellCount }?.let(::Erase)
                CLEAR -> Clear.takeIf { args.isEmpty() }
                UNDO -> Undo.takeIf { args.isEmpty() }
                REDO -> Redo.takeIf { args.isEmpty() }
                HINT_REVEAL -> cellDigit(args, geometry)?.let { (idx, digit) -> HintReveal(idx, digit) }
                HINT -> Hint.takeIf { args.isEmpty() }
                else -> null
            }
        }
    }
}

// Persisted in every stored replay: never renumber, only append.
private const val PLACE = 0
private const val TOGGLE_NOTE = 1
private const val ERASE = 2
private const val CLEAR = 3
private const val UNDO = 4
private const val REDO = 5
private const val HINT_REVEAL = 6
private const val HINT = 7

private fun cellDigit(args: IntArray, geometry: SudokuGeometry): Pair<Int, Int>? {
    if (args.size != 2) return null
    val (idx, digit) = args
    return if (idx in 0 until geometry.cellCount && digit in 1..geometry.size) idx to digit else null
}
