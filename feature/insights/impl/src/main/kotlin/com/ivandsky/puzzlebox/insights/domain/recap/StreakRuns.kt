package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.domain.ComputeStreak
import com.ivandsky.puzzlebox.domain.StreakFreezes
import java.time.LocalDate

/**
 * Every streak of a history as a run of days, read off the one streak rule ([ComputeStreak]) and defining none of its
 * own. The walk's answer is the solved days plus the days its freezes held ([StreakFreezes.frozenDays]): a gap it could
 * not bridge freezes nothing, so the days on either side of it are not consecutive and a streak ends there. A maximal
 * stretch of consecutive such days is therefore one streak, the longest is the game page's best streak, and the one
 * reaching today or yesterday is its current one (pinned by `StreakWalkCharacterizationTest`).
 *
 * Frozen days are a snapshot of the history as it is now: a result imported later can unfreeze a past month, as the
 * walk never stores them.
 */
internal class StreakRuns private constructor(private val runs: List<StreakRun>) {

    /** The part of each streak that falls inside [period], oldest first; a streak that never reaches it is left out. */
    fun within(period: RecapPeriod): List<StreakRun> = runs.mapNotNull { it.within(period) }

    companion object {
        /** The runs of [streakDays], the days [ComputeStreak] was given, with the [freezes] it answered. */
        fun of(streakDays: Collection<LocalDate>, freezes: StreakFreezes): StreakRuns {
            val days = (streakDays + freezes.frozenDays).toSortedSet().toList()
            val runs = mutableListOf<StreakRun>()
            var from = 0
            for (next in 1..days.size) {
                if (next < days.size && days[next - 1].plusDays(1) == days[next]) continue
                val start = days[from]
                val end = days[next - 1]
                runs += StreakRun(start, end, freezes.frozenDays.filterTo(mutableSetOf()) { it in start..end })
                from = next
            }
            return StreakRuns(runs)
        }
    }
}
