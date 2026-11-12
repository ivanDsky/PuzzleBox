package com.ivandsky.puzzlebox.game.replay

import com.ivandsky.puzzlebox.game.GameSession
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.SolveReplay

/**
 * One opened solve replay: the board at any play time, re-folded from the puzzle's initial session
 * through the game's step. Immutable. Safe to share across threads. It holds no thread and no
 * clock; the caller chooses the dispatcher.
 */
class Timeline private constructor(
    val game: GameId,
    val endMs: Long,
    val outcome: ReplayOutcome,
    private val fold: Fold<*>,
) {
    /** Distinct stamps of the moves that changed the board, ascending. None is above [endMs]. */
    val moveTimesMs: LongArray = fold.tickTimesMs()

    /** The board at [tMs], clamped to 0..[endMs]. Folds at most 64 steps. Never throws. */
    fun at(tMs: Long): ReplayFrame {
        val clampedMs = tMs.coerceIn(0, endMs)
        return fold.frame(clampedMs, ended = clampedMs == endMs)
    }

    /**
     * The typed half: [S] stops here, so a timeline carries no session type. [moves] holds only the
     * moves that changed the board; `checkpoints[i]` is the session after `i * CHECKPOINT_EVERY` of them.
     */
    private class Fold<S : GameSession>(
        private val replay: GameReplay<S>,
        private val facts: (S) -> ReplayFacts,
        private val moves: List<StampedMove>,
        private val checkpoints: List<S>,
    ) {
        // The one mutable field. A race is harmless: every Reached is immutable and a valid place
        // to fold from, so a lost write costs steps and never a wrong frame.
        @Volatile
        private var reached = Reached(applied = 0, session = checkpoints[0], before = null)

        fun tickTimesMs(): LongArray = moves.map { it.atMs }.distinct().toLongArray()

        fun frame(tMs: Long, ended: Boolean): ReplayFrame {
            val applied = appliedBy(tMs)
            val board = reach(applied)
            val sinceMoveMs = if (applied == 0) tMs else tMs - moves[applied - 1].atMs
            return ReplayFrame(board.session, board.before, facts(board.session), tMs, sinceMoveMs, ended)
        }

        /** How many moves are stamped at or before [tMs]. */
        private fun appliedBy(tMs: Long): Int {
            var low = 0
            var high = moves.size
            while (low < high) {
                val middle = (low + high) ushr 1
                if (moves[middle].atMs <= tMs) low = middle + 1 else high = middle
            }
            return low
        }

        private fun reach(applied: Int): Reached<S> {
            val last = reached
            if (last.applied == applied) return last
            val checkpoint = (applied - 1).coerceAtLeast(0) / CHECKPOINT_EVERY
            val checkpointApplied = checkpoint * CHECKPOINT_EVERY
            // Forward play goes on from the last frame. A seek back, or ahead past a checkpoint, starts at the checkpoint.
            val start = last.takeIf { it.applied in checkpointApplied until applied }
                ?: Reached(checkpointApplied, checkpoints[checkpoint], before = null)
            var session = start.session
            var before = start.before
            for (index in start.applied until applied) {
                before = session
                // open folded these same moves, so a pure step never refuses one here; at must not throw if one does.
                session = replay.step(session, moves[index].move, moves[index].atMs) ?: session
            }
            return Reached(applied, session, before).also { reached = it }
        }
    }

    /** The board after the first [applied] moves; [before] is the board one move earlier. */
    private class Reached<S>(val applied: Int, val session: S, val before: S?)

    companion object {
        private const val CHECKPOINT_EVERY = 64

        /**
         * Decodes [bytes] and folds every move once from [initial]; calls `replay.factsOf(initial)` once.
         * Null = no replay: unreadable bytes, another game, an Open outcome, a move the game does not
         * understand, or a Solved record that does not fold to a solved board. Never throws.
         */
        fun <S : GameSession> open(game: GameId, replay: GameReplay<S>, initial: S, bytes: SolveReplay): Timeline? {
            val record = ReplayCodec.decode(bytes.toByteArray()).getOrNull() ?: return null
            if (record.game != game || record.outcome == ReplayOutcome.Open) return null
            val facts = replay.factsOf(initial)
            val moves = ArrayList<StampedMove>(record.moves.size)
            val checkpoints = arrayListOf(initial)
            var session = initial
            for (stamped in record.moves) {
                // A decoded stamp may lie past endMs. Capped there, the frame at endMs is still the final board.
                val atMs = minOf(stamped.atMs, record.endMs)
                val next = replay.step(session, stamped.move, atMs) ?: return null
                if (next === session) continue
                session = next
                moves += StampedMove(atMs, stamped.move)
                if (moves.size % CHECKPOINT_EVERY == 0) checkpoints += session
            }
            if (record.outcome == ReplayOutcome.Solved && !facts(session).solved) return null
            return Timeline(game, record.endMs, record.outcome, Fold(replay, facts, moves, checkpoints))
        }
    }
}
