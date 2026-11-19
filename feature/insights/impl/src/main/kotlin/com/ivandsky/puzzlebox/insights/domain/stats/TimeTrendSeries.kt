package com.ivandsky.puzzlebox.insights.domain.stats

import com.ivandsky.puzzlebox.insights.domain.recap.PeriodFacts
import java.time.LocalDate

/**
 * The average solve time over the range, one [Point] per bucket with a counted solve: per day for the Week range, per
 * week otherwise ([granularity]). The average of a bucket is its play time over its solves, so with every game in the
 * scope it sums the dailies (A11). A line needs 2 points: with fewer ([isLine] false) the screen shows the value as a number.
 */
internal data class TimeTrendSeries(val granularity: StatsGranularity, val points: List<Point>) {

    /** The [averageMs] of the [solved] counted solves of the bucket starting [start]. */
    data class Point(val start: LocalDate, val averageMs: Long, val solved: Int)

    /** Whether there are enough points to draw a line. */
    val isLine: Boolean get() = points.size >= MIN_LINE_POINTS

    companion object {
        private const val MIN_LINE_POINTS = 2

        /** The trend of [facts] over [range]; its points are empty when the range has no counted solve. */
        fun of(facts: PeriodFacts, range: StatsRange): TimeTrendSeries {
            val granularity = StatsGranularity.of(range)
            return TimeTrendSeries(
                granularity,
                StatsBucket.of(facts.dayTallies, granularity, facts.period.first).map { Point(it.start, it.timeMs / it.solved, it.solved) },
            )
        }
    }
}
