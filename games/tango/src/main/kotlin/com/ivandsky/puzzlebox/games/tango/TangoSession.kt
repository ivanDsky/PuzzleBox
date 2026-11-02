package com.ivandsky.puzzlebox.games.tango

import com.ivandsky.puzzlebox.game.GameSession
import com.ivandsky.puzzlebox.games.tango.engine.TangoRules
import com.ivandsky.puzzlebox.games.tango.engine.TangoState
import java.time.Instant

/**
 * Serializable in-progress play (ZipSession shape, thinned to the walking-skeleton field
 * set): puzzle + player placements, the counters the record path needs, and [completedAt]
 * as the one-completion guard (A4). Givens live only on [puzzle]; [placements] is the
 * player-authored overlay keyed by row-major index.
 */
data class TangoSession(
    val ref: TangoPuzzleRef?,
    val puzzle: TangoPuzzle,
    val placements: Map<Int, TangoCell> = emptyMap(),
    val hintsUsed: Int = 0,
    val mistakeCount: Int = 0,
    val elapsedMs: Long = 0,
    val completedAt: Instant? = null,
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
            unknownTail.contentEquals(other.unknownTail)

    override fun hashCode(): Int {
        var h = ref.hashCode()
        h = 31 * h + puzzle.hashCode()
        h = 31 * h + placements.hashCode()
        h = 31 * h + hintsUsed
        h = 31 * h + mistakeCount
        h = 31 * h + elapsedMs.hashCode()
        h = 31 * h + completedAt.hashCode()
        h = 31 * h + unknownTail.contentHashCode()
        return h
    }

    val isComplete: Boolean get() = completedAt != null

    /**
     * Applies an ALREADY-VALIDATED move (the caller runs [TangoRules.validate] first).
     * Stamps [completedAt] the moment the grid becomes a full solution (A4: one play-through,
     * one completion); timer accrual rides [elapsedMs] — the clock mechanism is ticket-05 scope.
     */
    fun withMove(move: com.ivandsky.puzzlebox.games.tango.engine.TangoMove, now: Instant): TangoSession {
        if (completedAt != null) return this
        val value = move.value
        if (value == placements[move.idx]) return this
        val next = if (value == null) {
            copy(placements = placements - move.idx)
        } else {
            copy(placements = placements + (move.idx to value))
        }
        if (TangoRules.isComplete(TangoState(puzzle, next.grid()))) {
            return next.copy(completedAt = now)
        }
        return next
    }

    /** Invalid-drop rejection: a mistake for analytics, never scored (A3). */
    fun withRejection(): TangoSession = copy(mistakeCount = mistakeCount + 1)

    fun withHintApplied(): TangoSession = copy(hintsUsed = hintsUsed + 1)

    /** The full rules grid: givens under the player overlay. */
    fun grid(): List<TangoCell?> =
        List(puzzle.cellCount) { idx -> puzzle.givens[idx] ?: placements[idx] }
}
