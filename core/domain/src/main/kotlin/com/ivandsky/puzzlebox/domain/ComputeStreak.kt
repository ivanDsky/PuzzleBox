package com.ivandsky.puzzlebox.domain

/**
 * Captured-days streak (A8, cached-data-only teaser): the run of consecutive calendar
 * days ending at the newest captured day. Anchoring at the newest capture — not today
 * — keeps the teaser warm when today's puzzle simply hasn't been fetched yet; the
 * moment a day is missed the run honestly resets.
 *
 * Archived days (A23) are history, not habit: playing one never extends or bridges
 * the run, so they are dropped before counting.
 */
class ComputeStreak {

    operator fun invoke(days: List<GameHistoryDay>): Int {
        val sorted = days.filterNot(GameHistoryDay::archived).map(GameHistoryDay::day).distinct().sortedDescending()
        if (sorted.isEmpty()) return 0
        var streak = 1
        for (i in 1 until sorted.size) {
            if (sorted[i - 1].minusDays(1) == sorted[i]) streak++ else break
        }
        return streak
    }
}
