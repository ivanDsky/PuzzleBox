package com.ivandsky.puzzlebox.games.sudokuui

import com.ivandsky.puzzlebox.game.playTimeMs
import com.ivandsky.puzzlebox.game.replay.MoveLog
import com.ivandsky.puzzlebox.games.sudoku.SudokuReplay
import com.ivandsky.puzzlebox.games.sudoku.SudokuReplayMove
import com.ivandsky.puzzlebox.games.sudoku.SudokuSession
import java.time.Instant

/**
 * The play's [MoveLog] in Sudoku moves, and the only way a live Sudoku session changes: a move
 * is stamped with the play time it arrives at and stepped through [SudokuReplay], the reducer a
 * replay folds with, so the record and the board cannot differ.
 *
 * The defaults record nothing and stamp from a stopped clock: a board with no play behind it.
 */
class SudokuMoveLog(
    private val log: MoveLog = MoveLog.off(),
    private val now: () -> Instant = Instant::now,
    /** The start of the open clock window; null while the clock is stopped. */
    private val runningSince: () -> Instant? = { null },
) {

    /** The session to store. The same instance back: nothing happened and nothing was recorded. */
    fun record(session: SudokuSession, move: SudokuReplayMove): SudokuSession {
        val at = now()
        // Never the displayed time: that one lags by a tick.
        val atMs = playTimeMs(session.elapsedMs, runningSince(), at)
        val next = log.play(SudokuReplay, session, move.toReplayMove(), atMs)
        // The step stamps a win with its play time, all a replay has. A live win keeps the
        // wall-clock moment, in the session codec's millisecond precision.
        val wonNow = next.isComplete && !session.isComplete
        return if (wonNow) next.copy(completedAt = Instant.ofEpochMilli(at.toEpochMilli())) else next
    }
}
