package com.ivandsky.puzzlebox.insights.domain.stats

import com.ivandsky.puzzlebox.insights.domain.recap.PeriodFacts
import java.time.LocalDate

/**
 * The share of counted solves with no hint over the range, one [Point] per bucket with a counted solve: per day for the
 * Week range, per week otherwise ([granularity]). With every game in the scope it sums the dailies (A11). A line needs 2
 * points: with fewer ([isLine] false) the screen shows the value as a number.
 */
internal data class HintFreeRateSeries(val granularity: StatsGranularity, val points: List<Point>) {

    /** [hintFree] of the [solved] counted solves of the bucket starting [start] had no hint. */
    data class Point(val start: LocalDate, val hintFree: Int, val solved: Int) {

        /** The share of hint-free solves, 0 to 1. */
        val share: Double get() = hintFree.toDouble() / solved
    }

    /** Whether there are enough points to draw a line. */
    val isLine: Boolean get() = points.size >= MIN_LINE_POINTS

    companion object {
        private const val MIN_LINE_POINTS = 2

        /** The rate of [facts] over [range]; its points are empty when the range has no counted solve. */
        fun of(facts: PeriodFacts, range: StatsRange): HintFreeRateSeries {
            val granularity = StatsGranularity.of(range)
            return HintFreeRateSeries(
                granularity,
                StatsBucket.of(facts.dayTallies, granularity).map { Point(it.start, it.hintFree, it.solved) },
            )
        }
    }
}
