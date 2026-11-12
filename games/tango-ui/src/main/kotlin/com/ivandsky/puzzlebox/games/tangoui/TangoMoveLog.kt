package com.ivandsky.puzzlebox.games.tangoui

import com.ivandsky.puzzlebox.game.playTimeMs
import com.ivandsky.puzzlebox.game.replay.MoveLog
import com.ivandsky.puzzlebox.games.tango.TangoReplay
import com.ivandsky.puzzlebox.games.tango.TangoReplayMove
import com.ivandsky.puzzlebox.games.tango.TangoSession
import java.time.Instant

/**
 * The play's [MoveLog] in Tango moves, and the only way a live Tango session changes through input: a move
 * is stamped with the play time it arrives at and stepped through [TangoReplay], the reducer a replay folds
 * with, so the record and the board cannot differ.
 */
internal class TangoMoveLog(
    private val log: MoveLog,
    private val now: () -> Instant,
    /** The start of the open clock window; null while the clock is stopped. */
    private val runningSince: () -> Instant?,
) {

    /**
     * The session to store. The same instance back: nothing happened and nothing was recorded. One input that
     * lands several moves passes one [at], so they share a stamp.
     */
    fun record(session: TangoSession, move: TangoReplayMove, at: Instant = now()): TangoSession {
        // Never the displayed time: that one lags by a tick.
        val atMs = playTimeMs(session.elapsedMs, runningSince(), at)
        val next = log.play(TangoReplay, session, move.toReplayMove(), atMs)
        // The step stamps a win with its play time, all a replay has.
        // A live win keeps the wall-clock moment, in the session codec's millisecond precision.
        val wonNow = next.isComplete && !session.isComplete
        return if (wonNow) next.copy(completedAt = Instant.ofEpochMilli(at.toEpochMilli())) else next
    }
}
