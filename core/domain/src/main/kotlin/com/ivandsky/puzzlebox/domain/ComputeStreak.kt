package com.ivandsky.puzzlebox.domain

import java.time.LocalDate

/**
 * Solved-day streak (A8): the run of consecutive puzzle days with at least one solved
 * daily, anchored at today's puzzle day ([GetPuzzleDay]) — or yesterday when today has
 * no solve yet, so the run stays alive until the day is actually missed. Callers pass
 * only solved days; opened-but-unsolved days never count. Duplicates collapse. [best] is
 * the same rule's longest run, so both streaks live in this one file.
 */
class ComputeStreak(private val puzzleDay: GetPuzzleDay) {

    operator fun invoke(solvedDays: Collection<LocalDate>): Int {
        val solved = solvedDays.toHashSet()
        val today = puzzleDay()
        var day = if (today in solved) today else today.minusDays(1)
        var streak = 0
        while (day in solved) {
            streak++
            day = day.minusDays(1)
        }
        return streak
    }

    /** The longest run of consecutive solved days ever, wherever it falls; 0 with none. */
    fun best(solvedDays: Collection<LocalDate>): Int {
        var best = 0
        var run = 0
        var previous: LocalDate? = null
        for (day in solvedDays.toSortedSet()) {
            run = if (previous != null && day == previous.plusDays(1)) run + 1 else 1
            best = maxOf(best, run)
            previous = day
        }
        return best
    }
}
