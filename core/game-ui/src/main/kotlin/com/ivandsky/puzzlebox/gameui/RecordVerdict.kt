package com.ivandsky.puzzlebox.gameui

import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder

/**
 * The result's honest-best verdict (spec A10, A26, user ruling 2026-11-04): what THIS
 * solve means against the game's previous best — the top first solve by the game's rank
 * order, read at session start. A replay (a puzzle already solved before this session)
 * is practice on a known board: it never earns a best, and its score never becomes one.
 */
sealed interface RecordVerdict {
    /** Ranks strictly above the previous best at the shown resolution. */
    data object NewBest : RecordVerdict

    /** Shows the same score as the previous best. */
    data object TiedBest : RecordVerdict

    /** No first solve of the game existed before this one. */
    data object FirstSolve : RecordVerdict

    /** Ranks below the previous best. */
    data object NotBest : RecordVerdict

    /** This puzzle was already solved before the session started. */
    data object Replay : RecordVerdict

    companion object {
        /**
         * [previous] null = the game had no first solve before this one; [replay] wins
         * over every comparison. [order] is the game's rank order (ADR-0013): time-first
         * ignores hints, as its shown best does; hints-first ranks fewer hints first and
         * falls to time only on equal hints. Times compare in whole seconds, as
         * [formatDuration] shows them: a win by milliseconds that reads as the same time is a
         * tie, not a new best (the badge would contradict the best shown beside it).
         */
        fun of(previous: BestScore?, timeMs: Long, hints: Int, replay: Boolean, order: RankOrder): RecordVerdict = when {
            replay -> Replay
            previous == null -> FirstSolve
            order == RankOrder.HintsThenTime && hints < previous.hints -> NewBest
            order == RankOrder.HintsThenTime && hints > previous.hints -> NotBest
            timeMs / 1000 < previous.timeMs / 1000 -> NewBest
            timeMs / 1000 == previous.timeMs / 1000 -> TiedBest
            else -> NotBest
        }
    }
}
