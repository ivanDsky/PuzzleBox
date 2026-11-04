package com.ivandsky.puzzlebox.gameui

/**
 * The result's honest-best verdict (spec A10): what THIS solve's time means against the
 * game's previous best — the minimum time over every completion of the game, replays
 * included, read at session start.
 */
sealed interface RecordVerdict {
    /** Strictly lower than the previous best. */
    data object NewBest : RecordVerdict

    /** No completion of the game existed before this solve. */
    data object FirstSolve : RecordVerdict

    /** Equal to or slower than the previous best. */
    data object NotBest : RecordVerdict

    companion object {
        /** [previousBestMs] null = the game had no completion before this solve. */
        fun of(previousBestMs: Long?, timeMs: Long): RecordVerdict = when {
            previousBestMs == null -> FirstSolve
            timeMs < previousBestMs -> NewBest
            else -> NotBest
        }
    }
}
