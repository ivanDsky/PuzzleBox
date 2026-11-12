package com.ivandsky.puzzlebox.games.strandsui

import com.ivandsky.puzzlebox.game.playTimeMs
import com.ivandsky.puzzlebox.game.replay.MoveLog
import com.ivandsky.puzzlebox.games.strands.ActiveHint
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.StrandsReplay
import com.ivandsky.puzzlebox.games.strands.StrandsReplayMove
import com.ivandsky.puzzlebox.games.strands.StrandsSession
import java.time.Instant

/**
 * The play's [MoveLog] in Strands moves, and the only way a live Strands session changes: a move
 * is stamped with the play time it arrives at and stepped through [StrandsReplay], the reducer a
 * replay folds with, so the record and the board cannot differ.
 */
internal class StrandsMoveLog(
    private val log: MoveLog,
    private val now: () -> Instant,
    /** The start of the open clock window; null while the clock is stopped. */
    private val runningSince: () -> Instant?,
) {

    /** The session to store. The same instance back: nothing happened and nothing was recorded. */
    fun submit(session: StrandsSession, path: List<Coord>): StrandsSession =
        record(session, StrandsReplayMove.Submit(path))

    /** [hint] is the reveal the rules handed over; it is recorded as the word's position and stage. */
    fun hint(session: StrandsSession, hint: ActiveHint): StrandsSession {
        val puzzle = session.state.puzzle
        val index = when (hint.word) {
            puzzle.spangram.text -> puzzle.themeWords.size
            else -> puzzle.themeWords.indexOfFirst { it.text == hint.word }
        }
        return record(session, StrandsReplayMove.Hint(index, hint.stage))
    }

    private fun record(session: StrandsSession, move: StrandsReplayMove): StrandsSession {
        // Never the displayed time: that one lags by a tick.
        val atMs = playTimeMs(session.elapsedMs, runningSince(), now())
        return log.play(StrandsReplay, session, move.toReplayMove(session.state.puzzle.cols), atMs)
    }
}
