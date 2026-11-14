package com.ivandsky.puzzlebox.game.replay

import com.ivandsky.puzzlebox.game.GameSession
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.SolveReplay

/**
 * Journal of one play. Main-thread confined.
 *
 * A void log (off, nothing readable to restore, a move not understood, a cap passed) still steps,
 * records nothing and stays void for the rest of the solve, so a replay is complete or absent and
 * never truncated (A1, A12).
 */
class MoveLog private constructor(private var journal: Journal?) {

    /** Applies [move] through [replay]. Records it only when the session changed. Returns the session to store. */
    fun <S : GameSession> play(replay: GameReplay<S>, session: S, move: ReplayMove, atMs: Long): S =
        step(replay, session, move, atMs, isSetting = false)

    /**
     * Same for a setting, at adopt and at flip. Recorded even when the session did not change, so a replay knows the
     * setting from its first frame. Not recorded when it repeats the journal's latest move of its kind: a restored
     * play states its setting again, and its record has to equal the uninterrupted one.
     */
    fun <S : GameSession> setting(replay: GameReplay<S>, session: S, move: ReplayMove, atMs: Long): S =
        step(replay, session, move, atMs, isSetting = true)

    /**
     * Empties the journal for a new solve of the same game: play again after a restored play adopts the saved
     * session first, and what that adopt recorded at the saved time would raise every stamp of the new solve.
     * An off or void log stays as it was.
     */
    fun restart() {
        journal?.moves?.clear()
    }

    /** In-progress bytes for the saved play; null = nothing to save. */
    fun snapshot(): ByteArray? {
        val current = journal ?: return null
        return ReplayCodec.encode(ReplayRecord(current.game, ReplayOutcome.Open, endMs = 0, current.moves)) ?: voided()
    }

    /** The sealed replay; null = no replay for this solve. [outcome] is Solved or GaveUp: Open seals nothing. */
    fun seal(outcome: ReplayOutcome, endMs: Long): SolveReplay? {
        val current = journal ?: return null
        if (outcome == ReplayOutcome.Open) return null
        // Stamps never decrease, so the last one within the end time means all of them are.
        val moves = if (current.lastMs <= endMs) {
            current.moves
        } else {
            current.moves.map { if (it.atMs > endMs) StampedMove(endMs, it.move) else it }
        }
        return ReplayCodec.encode(ReplayRecord(current.game, outcome, endMs, moves))?.let(SolveReplay::of) ?: voided()
    }

    private fun <S : GameSession> step(
        replay: GameReplay<S>,
        session: S,
        move: ReplayMove,
        atMs: Long,
        isSetting: Boolean,
    ): S {
        val current = journal
        // The game steps with the stamp that is recorded, so a re-fold sees the time live play saw.
        val stampMs = if (current == null) atMs else maxOf(atMs, current.lastMs)
        val next = replay.step(session, move, stampMs)
        if (next == null) {
            journal = null
            return session
        }
        if (current == null) return next
        val changed = if (isSetting) current.latestOfKind(move.kind) != move else next !== session
        if (!changed) return next
        // Voided here, not at the next snapshot, so the journal never grows past the cap.
        if (current.moves.size == ReplayCodec.MAX_MOVES) {
            journal = null
        } else {
            current.moves += StampedMove(stampMs, move)
        }
        return next
    }

    private fun voided(): Nothing? {
        journal = null
        return null
    }

    private class Journal(val game: GameId, val moves: ArrayList<StampedMove>) {
        val lastMs: Long get() = if (moves.isEmpty()) 0 else moves.last().atMs

        fun latestOfKind(kind: Int): ReplayMove? = moves.lastOrNull { it.move.kind == kind }?.move
    }

    companion object {
        /** The void state from the start: steps only, snapshot and seal are always null. A game without a replay member, tutorials. */
        fun off(): MoveLog = MoveLog(journal = null)

        /** Records from move zero. */
        fun fresh(game: GameId): MoveLog = MoveLog(Journal(game, ArrayList()))

        /** Continues a saved log. A null, unreadable, sealed or wrong-game snapshot gives a void log. */
        fun restored(game: GameId, snapshot: ByteArray?): MoveLog {
            val record = snapshot?.let(ReplayCodec::decode)?.getOrNull()
            if (record == null || record.outcome != ReplayOutcome.Open || record.game != game) return off()
            return MoveLog(Journal(game, ArrayList(record.moves)))
        }
    }
}
