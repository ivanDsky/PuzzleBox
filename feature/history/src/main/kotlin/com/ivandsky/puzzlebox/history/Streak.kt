package com.ivandsky.puzzlebox.history

import java.time.LocalDate

/**
 * Captured-days streak (A8, cached-data-only teaser): the run of consecutive calendar
 * days ending at the newest captured day. Anchoring at the newest capture — not today
 * — keeps the teaser warm when today's puzzle simply hasn't been fetched yet; the
 * moment a day is missed the run honestly resets.
 */
fun streakEndingAtNewest(days: List<LocalDate>): Int {
    val sorted = days.distinct().sortedDescending()
    if (sorted.isEmpty()) return 0
    var streak = 1
    for (i in 1 until sorted.size) {
        if (sorted[i - 1].minusDays(1) == sorted[i]) streak++ else break
    }
    return streak
}
