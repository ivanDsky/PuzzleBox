package com.ivandsky.puzzlebox.insights.domain.stats

import com.ivandsky.puzzlebox.data.rankhistory.RankHistoryStore
import java.time.LocalDate

/** What the rank history section says once its read is in: the weeks kept for the viewer, or what unlocks them. */
internal sealed interface RankHistoryStanding {

    /**
     * The Friends points per played day of each kept week, oldest week first, as kept (A19): a value is never
     * recomputed. A line needs 2 weeks ([isLine], A14); with 1 the section shows the value as a number; with none
     * yet it says when weeks start to appear.
     */
    data class Kept(val weeks: List<Week>) : RankHistoryStanding {

        val isLine: Boolean get() = weeks.size >= MIN_LINE_WEEKS

        /** The week that starts on [start] (a Monday) and its Friends [pointsPerDay]. */
        data class Week(val start: LocalDate, val pointsPerDay: Double)

        companion object {
            private const val MIN_LINE_WEEKS = 2

            /** The weeks of one game's [kept] rows, oldest first, each with its points per day as kept. */
            fun of(kept: List<RankHistoryStore.Week>): Kept =
                Kept(kept.sortedBy { it.weekStart }.map { Week(it.weekStart, it.pointsPerDay) })
        }
    }

    /** There is nothing to show yet; the section is replaced by one plain line saying what [unlock]s it. */
    data class Locked(val unlock: VsFriendsStanding.Unlock) : RankHistoryStanding
}
