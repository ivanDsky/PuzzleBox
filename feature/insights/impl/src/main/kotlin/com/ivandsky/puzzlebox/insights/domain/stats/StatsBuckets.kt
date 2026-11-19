package com.ivandsky.puzzlebox.insights.domain.stats

import com.ivandsky.puzzlebox.domain.ranks.RankWindow
import com.ivandsky.puzzlebox.domain.ranks.days
import com.ivandsky.puzzlebox.insights.domain.recap.DayTally
import java.time.LocalDate

/** The size of one point of a stats line: a puzzle day for the Week range, a Mon–Sun week for Month and All (spec 2.4). */
internal enum class StatsGranularity {
    Day,
    Week,
    ;

    /** The first puzzle day of the bucket holding [day]; a week starts where the ranks' weekly boards start. */
    fun bucketStart(day: LocalDate): LocalDate = when (this) {
        Day -> day
        Week -> RankWindow.Week(day).days(today = day).first()
    }

    companion object {
        /** The granularity a [range] draws its lines in. */
        fun of(range: StatsRange): StatsGranularity = if (range == StatsRange.Week) Day else Week
    }
}

/** The counted solves of the puzzle days from [start] to the end of the bucket (only the days inside the range, so the first week of a range starts on the range's first day): [solved] of them, [timeMs] of play, [hintFree] with no hint. */
internal data class StatsBucket(val start: LocalDate, val solved: Int, val timeMs: Long, val hintFree: Int) {

    companion object {
        /**
         * [tallies] summed per bucket of [granularity], oldest first; a bucket with no solve is absent. A week that
         * begins before the range's [first] day starts there instead, so a bucket never names days outside the range.
         */
        fun of(tallies: List<DayTally>, granularity: StatsGranularity, first: LocalDate): List<StatsBucket> =
            tallies
                .groupBy { maxOf(granularity.bucketStart(it.day), first) }
                .map { (start, days) ->
                    StatsBucket(start, days.sumOf { it.solved }, days.sumOf { it.timeMs }, days.sumOf { it.hintFree })
                }
                .filter { it.solved > 0 }
                .sortedBy { it.start }
    }
}
