package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.domain.GameHistoryDay
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

/**
 * The longest stretch of counted solves with no hint, one after the other: [solves] of them from the puzzle day [start]
 * to [last]. The solves are taken in puzzle-day order and, within a day, in the app's game order (A11), so a run is the same on every read;
 * a solve that used a hint ends it, and a day with no solve neither ends nor extends it. [last] is the solve that
 * brought the run to its length.
 */
internal data class HintFreeRun(val solves: Int, val start: LocalDate, val last: SolveRef) {

    companion object {
        /**
         * The longest run in [counted] (counted solves only); the earliest when two tie. Null when none is hint-free.
         * Solves of one day follow [games], the app's game order; a game it does not list comes after those it does.
         */
        fun longestOf(counted: List<GameHistoryDay>, games: List<GameId>): HintFreeRun? {
            val ordered = counted.sortedWith(
                compareBy<GameHistoryDay>({ it.day }, { games.indexOf(it.gameId).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }, { it.gameId.value }),
            )
            var best: HintFreeRun? = null
            var runStart: LocalDate? = null
            var length = 0
            for (solve in ordered) {
                if (solve.record?.hints != 0) {
                    runStart = null
                    length = 0
                    continue
                }
                runStart = runStart ?: solve.day
                length++
                if (length > (best?.solves ?: 0)) best = HintFreeRun(length, runStart, SolveRef.of(solve))
            }
            return best
        }
    }
}
