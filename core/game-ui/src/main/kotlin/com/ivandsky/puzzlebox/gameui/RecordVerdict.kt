package com.ivandsky.puzzlebox.gameui

/**
 * The result's honest-best verdict (spec A10, user ruling 2026-11-04): what THIS solve's
 * time means against the game's previous best — the minimum time over the game's first
 * solves, read at session start. A replay (a puzzle already solved before this session)
 * is practice on a known board: it never earns a best, and its time never becomes one.
 */
sealed interface RecordVerdict {
    /** Strictly lower than the previous best at the shown resolution. */
    data object NewBest : RecordVerdict

    /** Shows the same time as the previous best. */
    data object TiedBest : RecordVerdict

    /** No first solve of the game existed before this one. */
    data object FirstSolve : RecordVerdict

    /** Shows slower than the previous best. */
    data object NotBest : RecordVerdict

    /** This puzzle was already solved before the session started. */
    data object Replay : RecordVerdict

    companion object {
        /**
         * [previousBestMs] null = the game had no first solve before this one; [replay]
         * wins over every time comparison. Times compare in whole seconds, as
         * [formatClock] shows them: a win by milliseconds that reads as the same time is a
         * tie, not a new best (the badge would contradict the best shown beside it).
         */
        fun of(previousBestMs: Long?, timeMs: Long, replay: Boolean): RecordVerdict = when {
            replay -> Replay
            previousBestMs == null -> FirstSolve
            timeMs / 1000 < previousBestMs / 1000 -> NewBest
            timeMs / 1000 == previousBestMs / 1000 -> TiedBest
            else -> NotBest
        }
    }
}
