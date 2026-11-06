package com.ivandsky.puzzlebox.domain

import java.time.LocalDate

/**
 * Solved-day streak (A8): the run of consecutive puzzle days with at least one solved
 * daily, anchored at today's puzzle day ([GetPuzzleDay]) — or yesterday when today has
 * no solve yet, so the run stays alive until the day is actually missed. Callers pass
 * only solved days; opened-but-unsolved days never count. Duplicates collapse.
 *
 * Archived days (A23) are history, not habit: playing one never extends or bridges
 * the run, so callers drop them before counting.
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
}
