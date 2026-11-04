package com.ivandsky.puzzlebox.games.zip

import com.ivandsky.puzzlebox.game.CommandHistory
import com.ivandsky.puzzlebox.game.GameSession
import com.ivandsky.puzzlebox.games.zip.engine.ZipMove
import com.ivandsky.puzzlebox.games.zip.engine.ZipRules
import com.ivandsky.puzzlebox.games.zip.engine.ZipState
import java.time.Duration
import java.time.Instant

/**
 * Serializable in-progress play (F2 §2 as amended by the §10 rulings). [elapsedMs]
 * accumulates ACTIVE foreground play only: the timer starts on the first successful
 * [ZipMove.Extend], pauses on [onBackground], resumes on [onForeground], and folds into
 * [elapsedMs] at completion. Replays are new sessions and emit again; one play-through
 * emits at most once (A4, guarded by [completedAt]).
 *
 * [runningSince] is the runtime anchor for the running timer and intentionally NOT
 * persisted: a restored session comes back paused at [elapsedMs] with [timerStarted]
 * preserved, so neither background gaps nor process-death gaps ever count (F2 §2).
 */
data class ZipSession(
    val ref: ZipPuzzleRef?,
    val puzzle: ZipPuzzle,
    val path: List<Cell> = emptyList(),
    val hintsUsed: Int = 0,
    val mistakeCount: Int = 0,
    val backtrackCount: Int = 0,
    val elapsedMs: Long = 0,
    val timerStarted: Boolean = false,
    val runningSince: Instant? = null,
    val completedAt: Instant? = null,
    /** Undo/redo over the trace ([path] is the board state commands edit); counters, timer
     *  and hints sit outside it, so undo never rewinds them. */
    val history: CommandHistory<ZipPath, ZipCommand> = CommandHistory(),
    /** Bytes a newer codec version appended (A19): carried by [ZipSessionCodec] and
     *  re-emitted verbatim so decode∘encode stays lossless across versions. */
    val unknownTail: ByteArray = ByteArray(0),
) : GameSession {

    override fun equals(other: Any?): Boolean =
        other is ZipSession &&
            ref == other.ref &&
            puzzle == other.puzzle &&
            path == other.path &&
            hintsUsed == other.hintsUsed &&
            mistakeCount == other.mistakeCount &&
            backtrackCount == other.backtrackCount &&
            elapsedMs == other.elapsedMs &&
            timerStarted == other.timerStarted &&
            runningSince == other.runningSince &&
            completedAt == other.completedAt &&
            history == other.history &&
            unknownTail.contentEquals(other.unknownTail)

    override fun hashCode(): Int {
        var h = ref.hashCode()
        h = 31 * h + puzzle.hashCode()
        h = 31 * h + path.hashCode()
        h = 31 * h + hintsUsed
        h = 31 * h + mistakeCount
        h = 31 * h + backtrackCount
        h = 31 * h + elapsedMs.hashCode()
        h = 31 * h + timerStarted.hashCode()
        h = 31 * h + (runningSince?.hashCode() ?: 0)
        h = 31 * h + (completedAt?.hashCode() ?: 0)
        h = 31 * h + history.hashCode()
        h = 31 * h + unknownTail.contentHashCode()
        return h
    }

    val isComplete: Boolean get() = completedAt != null

    /** What timer UI shows right now: banked time plus the running window. */
    fun displayedElapsedMs(now: Instant): Long =
        elapsedMs + (runningSince?.let { Duration.between(it, now).toMillis() } ?: 0L)

    /**
     * Applies an ALREADY-VALIDATED move (the caller runs [ZipRules.validateMove] first).
     * Starts the timer on the first Extend, counts retracts as backtracks, records the
     * matching [ZipCommand] (a no-op move records nothing and keeps the redo stack), and
     * folds the timer + stamps [completedAt] the moment the path becomes a full solution.
     */
    fun withMove(move: ZipMove, now: Instant): ZipSession {
        if (completedAt != null) return this // one play-through, one completion
        var next = this
        val command: ZipCommand
        when (move) {
            is ZipMove.Extend -> {
                if (path.isEmpty() && !timerStarted) next = next.copy(timerStarted = true)
                val anchor = next.runningSince ?: now // first Extend starts the clock
                command = ZipCommand.Extend(move.to)
                next = next.copy(path = command.apply(path), runningSince = anchor)
            }

            is ZipMove.RetractTo -> {
                val index = path.indexOf(move.to)
                if (index < 0 || index == path.lastIndex) return this
                command = ZipCommand.RetractTo(move.to, removedTail = path.subList(index + 1, path.size).toList())
                next = next.copy(path = command.apply(path), backtrackCount = backtrackCount + 1)
            }

            ZipMove.Reset -> {
                if (path.isEmpty()) return this
                command = ZipCommand.Reset(removedPath = path)
                next = next.copy(path = command.apply(path), backtrackCount = backtrackCount + 1)
            }
        }
        next = next.copy(history = history.record(command).capped())
        if (isWinningPath(next)) {
            val banked = next.elapsedMs + (next.runningSince?.let { Duration.between(it, now).toMillis() } ?: 0L)
            next = next.copy(
                elapsedMs = banked,
                runningSince = null,
                completedAt = now,
            )
        }
        return next
    }

    /**
     * Reverts the newest command. Only the trace moves: the timer, hints, mistakes and
     * backtracks stay as played. No-op (returns this) with nothing to undo or once the
     * session is complete — a finished play-through is frozen.
     */
    fun withUndo(): ZipSession {
        if (completedAt != null) return this
        val step = history.undo(path) ?: return this
        return copy(path = step.state, history = step.history)
    }

    /** Re-applies the most recently undone command; same freeze/no-op rules as [withUndo]. */
    fun withRedo(): ZipSession {
        if (completedAt != null) return this
        val step = history.redo(path) ?: return this
        return copy(path = step.state, history = step.history)
    }

    /** Invalid-drop rejection: a mistake for analytics, never scored (A3). */
    fun withRejection(): ZipSession = copy(mistakeCount = mistakeCount + 1)

    /** One counted hint (the free trim preceding it is applied by the caller, uncounted). */
    fun withHintApplied(): ZipSession = copy(hintsUsed = hintsUsed + 1)

    fun onBackground(now: Instant): ZipSession {
        val anchor = runningSince ?: return this
        return copy(
            elapsedMs = elapsedMs + Duration.between(anchor, now).toMillis(),
            runningSince = null,
        )
    }

    fun onForeground(now: Instant): ZipSession {
        if (completedAt != null || runningSince != null || !timerStarted) return this
        return copy(runningSince = now)
    }

    private fun isWinningPath(session: ZipSession): Boolean =
        rules.isComplete(ZipState(puzzle, session.path))

    /** Oldest commands fall off so a long play never outgrows the codec's history bound. */
    private fun CommandHistory<ZipPath, ZipCommand>.capped(): CommandHistory<ZipPath, ZipCommand> =
        if (done.size <= MAX_HISTORY) this else CommandHistory(done.takeLast(MAX_HISTORY), undone)

    companion object {
        /** Newest undoable steps kept; well under the codec's decode bound. */
        const val MAX_HISTORY = 1_000

        private val rules = ZipRules
    }
}
