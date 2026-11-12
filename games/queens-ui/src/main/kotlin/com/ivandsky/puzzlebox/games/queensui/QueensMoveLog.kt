package com.ivandsky.puzzlebox.games.queensui

import com.ivandsky.puzzlebox.game.playTimeMs
import com.ivandsky.puzzlebox.game.replay.MoveLog
import com.ivandsky.puzzlebox.games.queens.QueensReplay
import com.ivandsky.puzzlebox.games.queens.QueensReplayMove
import com.ivandsky.puzzlebox.games.queens.QueensSession
import java.time.Instant

/**
 * The play's [MoveLog] in Queens moves, and the only way a live Queens session changes: a move
 * is stamped with the play time it arrives at and stepped through [QueensReplay], the reducer a
 * replay folds with, so the record and the board cannot differ.
 *
 * The defaults record nothing and stamp from a stopped clock: a board with no play behind it.
 */
class QueensMoveLog(
    private val log: MoveLog = MoveLog.off(),
    private val now: () -> Instant = Instant::now,
    /** The start of the open clock window; null while the clock is stopped. */
    private val runningSince: () -> Instant? = { null },
) {

    /** The session to store. The same instance back: nothing happened and nothing was recorded. */
    fun record(session: QueensSession, move: QueensReplayMove): QueensSession {
        val at = now()
        // Never the displayed time: that one lags by a tick.
        val atMs = playTimeMs(session.elapsedMs, runningSince(), at)
        val next = if (move is QueensReplayMove.AutoCross) {
            // A saved session does not carry the setting, so the record states it even when it equals the default.
            log.setting(QueensReplay, session, move.toReplayMove(), atMs)
        } else {
            log.play(QueensReplay, session, move.toReplayMove(), atMs)
        }
        // The step stamps a win with its play time, all a replay has. A live win keeps the
        // wall-clock moment, in the session codec's millisecond precision.
        val wonNow = next.isComplete && !session.isComplete
        return if (wonNow) next.copy(completedAt = Instant.ofEpochMilli(at.toEpochMilli())) else next
    }
}
