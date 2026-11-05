package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.SudokuCommand
import com.ivandsky.puzzlebox.games.sudoku.SudokuSession
import com.ivandsky.puzzlebox.games.sudoku.SudokuUnit
import com.ivandsky.puzzlebox.games.sudoku.UnitKind

/** What a stored session change meant to the player, for motion (A12) and haptics (A13). */
internal sealed interface SudokuPlayerEvent {

    /** Undo, redo, notes, Clear, the notes-mode toggle, a hint count: nothing to play. */
    data object None : SudokuPlayerEvent

    /** A digit committed at [idx] by the keypad or a hint reveal; [completedUnits] it just closed. */
    data class Placed(val idx: Int, val completedUnits: List<SudokuUnit>) : SudokuPlayerEvent

    /** [digit] removed from [idx]. */
    data class Erased(val idx: Int, val digit: Int) : SudokuPlayerEvent

    /** The commit at [idx] solved the board. */
    data class Solved(val idx: Int) : SudokuPlayerEvent
}

internal enum class SudokuHaptic { Tick, Confirm }

/**
 * Classifies [previous] → [next]. Only a newly recorded command counts: undo and redo move
 * existing commands between the stacks and stay silent.
 */
internal fun sudokuPlayerEvent(previous: SudokuSession, next: SudokuSession): SudokuPlayerEvent {
    val command = recordedCommand(previous, next) ?: return SudokuPlayerEvent.None
    val idx = when (command) {
        is SudokuCommand.Place -> command.idx
        is SudokuCommand.HintReveal -> command.idx
        is SudokuCommand.Erase -> {
            val digit = previous.board.digitAt(command.idx)
            return if (digit == 0) SudokuPlayerEvent.None else SudokuPlayerEvent.Erased(command.idx, digit)
        }
        is SudokuCommand.ToggleNote, is SudokuCommand.Clear -> return SudokuPlayerEvent.None
    }
    if (!previous.isComplete && next.isComplete) return SudokuPlayerEvent.Solved(idx)
    return SudokuPlayerEvent.Placed(idx, newlyCompletedUnits(previous.board, next.board, idx))
}

/** The commit's own haptic. A solve ticks now; its CONFIRM follows the solve run. */
internal fun sudokuHapticFor(event: SudokuPlayerEvent): SudokuHaptic? = when (event) {
    is SudokuPlayerEvent.Placed -> if (event.completedUnits.isEmpty()) SudokuHaptic.Tick else SudokuHaptic.Confirm
    is SudokuPlayerEvent.Solved -> SudokuHaptic.Tick
    is SudokuPlayerEvent.Erased, SudokuPlayerEvent.None -> null
}

internal fun SudokuHaptic.feedbackType(): HapticFeedbackType = when (this) {
    SudokuHaptic.Tick -> HapticFeedbackType.TextHandleMove
    SudokuHaptic.Confirm -> HapticFeedbackType.Confirm
}

internal fun HapticFeedback.perform(haptic: SudokuHaptic) = performHapticFeedback(haptic.feedbackType())

private fun recordedCommand(previous: SudokuSession, next: SudokuSession): SudokuCommand? {
    val before = previous.history
    val after = next.history
    if (after.done.size != before.done.size + 1 || after.undone.isNotEmpty()) return null
    val command = after.done.last()
    // Redo of the last undone command has the same stack shape as a new record; redo moves
    // the very same instance, a new record never does.
    return if (command === before.undone.lastOrNull()) null else command
}

private fun newlyCompletedUnits(before: SudokuBoard, after: SudokuBoard, idx: Int): List<SudokuUnit> {
    val geometry = after.geometry
    val touched = listOf(
        SudokuUnit(UnitKind.ROW, geometry.rowOf(idx)),
        SudokuUnit(UnitKind.COLUMN, geometry.colOf(idx)),
        SudokuUnit(UnitKind.BOX, geometry.boxOf(idx)),
    )
    return touched.filter { unit ->
        val cells = geometry.cellsOf(unit)
        isCompleteUnit(after, cells) && !isCompleteUnit(before, cells)
    }
}

/** Every cell filled and no digit twice: complete with no conflict. */
private fun isCompleteUnit(board: SudokuBoard, cells: IntArray): Boolean {
    var seen = 0
    for (cell in cells) {
        val digit = board.digitAt(cell)
        if (digit == 0) return false
        val bit = 1 shl (digit - 1)
        if (seen and bit != 0) return false
        seen = seen or bit
    }
    return true
}
