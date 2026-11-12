package com.ivandsky.puzzlebox.games.zipui

import com.ivandsky.puzzlebox.game.playTimeMs
import com.ivandsky.puzzlebox.game.replay.MoveLog
import com.ivandsky.puzzlebox.games.zip.ZipReplay
import com.ivandsky.puzzlebox.games.zip.ZipReplayMove
import com.ivandsky.puzzlebox.games.zip.ZipSession
import java.time.Instant

/**
 * The play's [MoveLog] in Zip moves, and the only way a live Zip trace changes: a move is
 * stamped with the play time it arrives at and stepped through [ZipReplay], the reducer a
 * replay folds with, so the record and the board cannot differ.
 */
internal class ZipMoveLog(
    private val log: MoveLog,
    private val now: () -> Instant,
) {

    /** The session to store. The same instance back: nothing happened and nothing was recorded. */
    fun record(session: ZipSession, move: ZipReplayMove): ZipSession {
        val at = now()
        // Never the displayed time: that one lags by a tick.
        val atMs = playTimeMs(session.elapsedMs, session.runningSince, at)
        val next = log.play(ZipReplay, session, move.toReplayMove(), atMs)
        // The step folds the clock at its play-time stamp, all a replay has. A live win keeps the
        // wall-clock moment and the play time the window ran to.
        val wonNow = next.isComplete && !session.isComplete
        return if (wonNow) next.copy(elapsedMs = atMs, completedAt = at) else next
    }
}
