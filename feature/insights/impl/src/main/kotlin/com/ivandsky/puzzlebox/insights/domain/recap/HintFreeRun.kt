package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.domain.GameHistoryDay
import java.time.LocalDate

/**
 * The longest stretch of counted solves with no hint, one after the other: [solves] of them from the puzzle day [start]
 * to [last]. The solves are taken in puzzle-day order and, within a day, by game id, so a run is the same on every read;
 * a solve that used a hint ends it, and a day with no solve neither ends nor extends it. [last] is the solve that
 * brought the run to its length.
 */
internal data class HintFreeRun(val solves: Int, val start: LocalDate, val last: SolveRef) {

    companion object {
        /** The longest run in [counted] (counted solves only); the earliest when two tie. Null when none is hint-free. */
        fun longestOf(counted: List<GameHistoryDay>): HintFreeRun? {
            val ordered = counted.sortedWith(compareBy<GameHistoryDay>({ it.day }, { it.gameId.value }))
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
