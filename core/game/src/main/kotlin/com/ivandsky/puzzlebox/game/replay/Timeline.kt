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
    private val frames: TimelineFrames<*>,
) {
    /** Distinct stamps of the moves that changed the board, ascending. None is above [endMs]. */
    val moveTimesMs: LongArray = frames.tickTimesMs()

    /**
     * What each tick of [moveTimesMs] was, parallel to it. Moves sharing a stamp are one tick; the
     * highest of Mistake > Hint > Undo > Normal wins. A Mistake the game calls transient
     * ([GameReplay.isTransientMistake]) and that the next tick lands within [MISTAKE_SETTLE_MS] of is
     * not one: it takes the highest non-Mistake kind of its own moves. A group that [GameReplay.continuesTap]
     * folds into the next one, which starts within [MISTAKE_SETTLE_MS], is no tick of its own: it takes
     * the next group's stamp, so one tap cycle is one tick.
     */
    val moveKindsAt: Array<MoveKind> = frames.tickKinds()

    /** The board at [tMs], clamped to 0..[endMs]. Folds at most 64 steps. Never throws. */
    fun at(tMs: Long): ReplayFrame {
        val clampedMs = tMs.coerceIn(0, endMs)
        return frames.frame(clampedMs, ended = clampedMs == endMs)
    }

    companion object {
        /**
         * A Mistake whose next stamp group lands less than this later may be a transient one the player
         * undid at once ([GameReplay.isTransientMistake]); the live twin is MotionTokens.SettleMillis, the
         * delay before a new violation shows. core:game cannot see the design system, so the value is repeated.
         */
        const val MISTAKE_SETTLE_MS = 500L

        /**
         * Decodes [bytes] and folds every move once from [initial]; calls `replay.factsOf(initial)` once.
         * Null = no replay: unreadable bytes, another game, an Open outcome, a move the game does not
         * understand, or a Solved record that does not fold to a solved board. Never throws.
         */
        fun <S : GameSession> open(game: GameId, replay: GameReplay<S>, initial: S, bytes: SolveReplay): Timeline? {
            val record = ReplayCodec.decode(bytes.toByteArray()).getOrNull() ?: return null
            if (record.game != game || record.outcome == ReplayOutcome.Open) return null
            val facts = replay.factsOf(initial)
            val fold = TimelineFold(replay, initial, record.endMs)
            for (stamped in record.moves) if (!fold.add(stamped)) return null
            fold.finish()
            if (record.outcome == ReplayOutcome.Solved && !facts(fold.session).solved) return null
            return Timeline(game, record.endMs, record.outcome, fold.frames(facts))
        }
    }
}
