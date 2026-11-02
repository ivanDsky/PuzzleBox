package com.ivandsky.puzzlebox.games.zip

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
) : GameSession {

    val isComplete: Boolean get() = completedAt != null

    /** What timer UI shows right now: banked time plus the running window. */
    fun displayedElapsedMs(now: Instant): Long =
        elapsedMs + (runningSince?.let { Duration.between(it, now).toMillis() } ?: 0L)

    /**
     * Applies an ALREADY-VALIDATED move (the caller runs [ZipRules.validateMove] first).
     * Starts the timer on the first Extend, counts retracts as backtracks, and folds the
     * timer + stamps [completedAt] the moment the path becomes a full solution.
     */
    fun withMove(move: ZipMove, now: Instant): ZipSession {
        if (completedAt != null) return this // one play-through, one completion
        var next = this
        when (move) {
            is ZipMove.Extend -> {
                if (path.isEmpty() && !timerStarted) next = next.copy(timerStarted = true)
                val anchor = next.runningSince ?: now // first Extend starts the clock
                next = next.copy(path = path + move.to, runningSince = anchor)
            }

            is ZipMove.RetractTo -> {
                val index = path.indexOf(move.to)
                if (index < 0 || index == path.lastIndex) return this
                next = next.copy(
                    path = path.subList(0, index + 1).toList(),
                    backtrackCount = backtrackCount + 1,
                )
            }

            ZipMove.Reset -> {
                if (path.isEmpty()) return this
                next = next.copy(path = emptyList(), backtrackCount = backtrackCount + 1)
            }
        }
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

    private companion object {
        val rules = ZipRules
    }
}
