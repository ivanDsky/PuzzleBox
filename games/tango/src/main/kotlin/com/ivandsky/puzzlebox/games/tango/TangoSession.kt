package com.ivandsky.puzzlebox.games.tango

import com.ivandsky.puzzlebox.game.CommandHistory
import com.ivandsky.puzzlebox.game.GameSession
import com.ivandsky.puzzlebox.games.tango.engine.TangoHint
import com.ivandsky.puzzlebox.games.tango.engine.TangoRules
import com.ivandsky.puzzlebox.games.tango.engine.TangoState
import java.time.Instant

/**
 * Serializable in-progress play (ZipSession shape, thinned to the walking-skeleton field
 * set): puzzle + player placements, the counters the record path needs, and [completedAt]
 * as the one-completion guard (A4). Givens live only on [puzzle]; [placements] is the
 * player-authored overlay keyed by row-major index. [history] is the undo/redo stack of
 * [TangoCommand]s over [placements]; it never touches the counters or the clock.
 */
data class TangoSession(
    val ref: TangoPuzzleRef?,
    val puzzle: TangoPuzzle,
    val placements: TangoPlacements = emptyMap(),
    val hintsUsed: Int = 0,
    val mistakeCount: Int = 0,
    val elapsedMs: Long = 0,
    val completedAt: Instant? = null,
    val history: CommandHistory<TangoPlacements, TangoCommand> = CommandHistory(),
    /** Bytes a newer codec version appended (A19), re-emitted verbatim by [TangoSessionCodec]. */
    val unknownTail: ByteArray = ByteArray(0),
) : GameSession {

    override fun equals(other: Any?): Boolean =
        other is TangoSession &&
            ref == other.ref &&
            puzzle == other.puzzle &&
            placements == other.placements &&
            hintsUsed == other.hintsUsed &&
            mistakeCount == other.mistakeCount &&
            elapsedMs == other.elapsedMs &&
            completedAt == other.completedAt &&
            history == other.history &&
            unknownTail.contentEquals(other.unknownTail)

    override fun hashCode(): Int {
        var h = ref.hashCode()
        h = 31 * h + puzzle.hashCode()
        h = 31 * h + placements.hashCode()
        h = 31 * h + hintsUsed
        h = 31 * h + mistakeCount
        h = 31 * h + elapsedMs.hashCode()
        h = 31 * h + completedAt.hashCode()
        h = 31 * h + history.hashCode()
        h = 31 * h + unknownTail.contentHashCode()
        return h
    }

    val isComplete: Boolean get() = completedAt != null

    /**
     * Applies an ALREADY-VALIDATED move (the caller runs [TangoRules.validate] first) as one
     * recorded [TangoPlace]; a move that changes nothing records nothing (and keeps redo).
     * Stamps [completedAt] the moment the grid becomes a full solution (A4: one play-through,
     * one completion); timer accrual rides [elapsedMs] — the clock mechanism is ticket-05 scope.
     */
    fun withMove(move: com.ivandsky.puzzlebox.games.tango.engine.TangoMove, now: Instant): TangoSession {
        if (completedAt != null) return this
        val place = TangoPlace(move.idx, move.value, previous = placements[move.idx])
        if (place.value == place.previous) return this
        val next = copy(placements = place.apply(placements), history = history.record(place))
        if (TangoRules.isComplete(TangoState(puzzle, next.grid()))) {
            return next.copy(completedAt = now)
        }
        return next
    }

    /**
     * Clear: wipes every player placement (givens stay) as ONE recorded [TangoClear], so a
     * single undo restores the whole board. Nothing to wipe, or a completed session (the
     * win is final), is a no-op that records nothing and keeps redo.
     */
    fun withCleared(): TangoSession {
        if (completedAt != null || placements.isEmpty()) return this
        return copy(placements = emptyMap(), history = history.record(TangoClear(placements)))
    }

    /**
     * Reverts the newest command. Not allowed once complete (the win is final) or with
     * nothing to undo. Counters and the clock are session facts, not board state: untouched.
     * Redo needs no completion check — completion freezes the history, so no redo path can
     * end on a board that was not already seen incomplete.
     */
    fun withUndo(): TangoSession {
        if (completedAt != null) return this
        val step = history.undo(placements) ?: return this
        return copy(placements = step.state, history = step.history)
    }

    /** Re-applies the most recently undone command; same guards as [withUndo]. */
    fun withRedo(): TangoSession {
        if (completedAt != null) return this
        val step = history.redo(placements) ?: return this
        return copy(placements = step.state, history = step.history)
    }

    /** Invalid-drop rejection: a mistake for analytics, never scored (A3). */
    fun withRejection(): TangoSession = copy(mistakeCount = mistakeCount + 1)

    /**
     * Counts a hint as consumed (A3) only when it actually forced a move — a [TangoHint.Stuck]
     * answer carries no information and must not cost the player (I7).
     */
    fun withHintApplied(hint: TangoHint): TangoSession =
        if (hint is TangoHint.Forced) copy(hintsUsed = hintsUsed + 1) else this

    /** The full rules grid: givens under the player overlay. */
    fun grid(): List<TangoCell?> =
        List(puzzle.cellCount) { idx -> puzzle.givens[idx] ?: placements[idx] }
}
