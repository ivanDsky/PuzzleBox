package com.ivandsky.puzzlebox.gameui

/**
 * The result's honest-best verdict (spec A10): what THIS solve's time means against the
 * game's previous best — the minimum time over every completion of the game, replays
 * included, read at session start.
 */
sealed interface RecordVerdict {
    /** Strictly lower than the previous best at the shown resolution. */
    data object NewBest : RecordVerdict

    /** No completion of the game existed before this solve. */
    data object FirstSolve : RecordVerdict

    /** Shows equal to or slower than the previous best. */
    data object NotBest : RecordVerdict

    companion object {
        /**
         * [previousBestMs] null = the game had no completion before this solve. Times
         * compare in whole seconds, as [formatClock] shows them: a win by milliseconds
         * that reads as the same time is not a new best (the badge would contradict the
         * "Previous best" beside it).
         */
        fun of(previousBestMs: Long?, timeMs: Long): RecordVerdict = when {
            previousBestMs == null -> FirstSolve
            timeMs / 1000 < previousBestMs / 1000 -> NewBest
            else -> NotBest
        }
    }
}
