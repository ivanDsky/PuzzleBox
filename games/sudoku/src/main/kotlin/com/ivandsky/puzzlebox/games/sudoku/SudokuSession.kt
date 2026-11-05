package com.ivandsky.puzzlebox.games.sudoku

import com.ivandsky.puzzlebox.game.CommandHistory
import com.ivandsky.puzzlebox.game.GameSession
import java.time.Instant

/**
 * In-progress play. [board] is the stored truth the [history] commands edit. [completedAt],
 * [elapsedMs], [hintsUsed] and [notesMode] live outside the history, so undoing a move never
 * rewinds the clock, a hint count or a setting. [elapsedMs] is banked play time; the play
 * controller owns the running window.
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

    /** Applies and records [command]; the first solve stamps [completedAt]. A won session is final. */
    fun withCommand(command: SudokuCommand, now: Instant): SudokuSession {
        if (isComplete) return this
        val next = copy(board = command.apply(board), history = history.record(command))
        if (!SudokuGameRules.isComplete(SudokuState(puzzle, next.board))) return next
        // The codec stores epoch millis; truncating here keeps a session equal to its own round trip.
        return next.copy(completedAt = Instant.ofEpochMilli(now.toEpochMilli()))
    }
}
