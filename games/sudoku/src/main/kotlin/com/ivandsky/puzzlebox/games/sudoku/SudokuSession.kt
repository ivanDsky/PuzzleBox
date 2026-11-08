package com.ivandsky.puzzlebox.games.sudoku

import com.ivandsky.puzzlebox.game.CommandHistory
import com.ivandsky.puzzlebox.game.GameSession
import com.ivandsky.puzzlebox.games.sudoku.engine.rules.SudokuConflicts
import java.time.Instant

/**
 * In-progress play. [board] is the stored truth the [history] commands edit. [completedAt],
 * [elapsedMs], [hintsUsed] and [notesMode] live outside the history, so undoing a move never
 * rewinds the clock, a hint count or a setting. [elapsedMs] is banked play time; the play
 * controller owns the running window.
 *
 * A won session is final: commands, undo, redo and hints leave it unchanged.
 */
data class SudokuSession(
    val ref: SudokuPuzzleRef?,
    val puzzle: SudokuPuzzle,
    val board: SudokuBoard = SudokuBoard.of(puzzle),
    val history: CommandHistory<SudokuBoard, SudokuCommand> = CommandHistory(),
    val completedAt: Instant? = null,
    val elapsedMs: Long = 0L,
    val hintsUsed: Int = 0,
    val notesMode: Boolean = false,
) : GameSession {

    val isComplete: Boolean get() = completedAt != null

    val canUndo: Boolean get() = !isComplete && history.canUndo

    val canRedo: Boolean get() = !isComplete && history.canRedo

    /**
     * Applies and records [command]. The first board that satisfies the rules stamps
     * [completedAt], whether or not it matches the stored solution.
     */
    fun withCommand(command: SudokuCommand, now: Instant): SudokuSession {
        if (isComplete) return this
        val next = copy(board = command.apply(board), history = history.record(command))
        if (!SudokuConflicts.isSolved(next.board)) return next
        // The codec stores epoch millis; truncating here keeps a session equal to its own round trip.
        return next.copy(completedAt = Instant.ofEpochMilli(now.toEpochMilli()))
    }

    fun withUndo(): SudokuSession {
        if (isComplete) return this
        val step = history.undo(board) ?: return this
        return copy(board = step.state, history = step.history)
    }

    // Redo only revisits boards withCommand already checked, so it can never be the winning move.
    fun withRedo(): SudokuSession {
        if (isComplete) return this
        val step = history.redo(board) ?: return this
        return copy(board = step.state, history = step.history)
    }

    fun withHintUsed(): SudokuSession = if (isComplete) this else copy(hintsUsed = hintsUsed + 1)

    /** A setting, not a move: it is never recorded, so undo does not flip it back. */
    fun withNotesMode(on: Boolean): SudokuSession = if (notesMode == on) this else copy(notesMode = on)
}
