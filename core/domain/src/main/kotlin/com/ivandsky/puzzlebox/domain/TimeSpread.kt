package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder

/**
 * What the Results histogram and caption need for one scope (every day, or one weekday), from
 * its earlier solves plus this one. Bins are over time for every game; [beaten], [best] and
 * [rank] follow the game's [RankOrder].
 *
 * @property solves earlier solves plus this one.
 * @property averageEarlierMs the earlier solves' average time; null on a first solve.
 * @property bins contiguous bins of round-time edges that hold every solve; empty slots stay.
 * @property youBin the bin holding this solve.
 * @property beaten earlier solves this one beats by the rank order; ties are not beaten.
 * @property best this solve beats every earlier one.
 * @property rank 1 plus the earlier solves that beat this one; ties do not count.
 */
data class TimeSpread(
    val solves: Int,
    val averageEarlierMs: Long?,
    val bins: List<SpreadBin>,
    val youBin: Int,
    val beaten: Int,
    val best: Boolean,
    val rank: Int,
) {
    val isFirstSolve: Boolean get() = solves == 1
}

/** Bin widths in seconds, smallest first: every bin edge is a round time. */
private val NiceStepsSeconds = intArrayOf(5, 10, 15, 20, 30, 60, 120, 180, 300, 600, 900, 1200, 1800, 3600, 7200, 10800)
private const val MIN_BAR_HEIGHT = 0.06f
private const val MIN_TALLEST = 3
private const val SINGLE_BAR_THIRDS = 3

/** The bin count aimed for over [solves] solves; the nice step may land under it. */
private fun targetBins(solves: Int): Int = when {
    solves <= 1 -> 1
    solves <= 4 -> solves
    solves <= 8 -> 5
    solves <= 16 -> 6
    solves <= 40 -> 8
    else -> 10
}

/**
 * Bins [earlier] plus [current] times with the smallest nice step that fits [targetBins], and
 * counts how many of [earlier] [current] beats under [order]. When every time is the same the
 * step is at least a third of it, so the one bar does not sit in a sliver range.
 */
fun timeSpread(earlier: List<BestScore>, current: BestScore, order: RankOrder): TimeSpread {
    val beaten = earlier.count { order.comparator.compare(current, it) < 0 }
    val ahead = earlier.count { order.comparator.compare(it, current) < 0 }
    val times = earlier.map { it.timeMs } + current.timeMs
    val min = times.min()
    val max = times.max()
    val target = targetBins(times.size)
    val floorStepMs = if (min == max) min / SINGLE_BAR_THIRDS else 0L
    val stepMs = NiceStepsSeconds.map { it * 1_000L }.let { steps ->
        steps.firstOrNull { it >= floorStepMs && max / it - min / it + 1 <= target } ?: steps.last()
    }
    val first = min / stepMs
    val count = (max / stepMs - first + 1).toInt()
    fun binOf(timeMs: Long): Int = (timeMs / stepMs - first).toInt()

    val counts = IntArray(count)
    times.forEach { counts[binOf(it)]++ }
    val plot = maxOf(counts.max(), MIN_TALLEST)
    val bins = counts.mapIndexed { index, n ->
        SpreadBin(
            count = n,
            height = if (n == 0) 0f else maxOf(MIN_BAR_HEIGHT, n.toFloat() / plot),
            fromMs = (first + index) * stepMs,
            toMs = (first + index + 1) * stepMs,
        )
    }
    return TimeSpread(
        solves = times.size,
        averageEarlierMs = if (earlier.isEmpty()) null else earlier.sumOf { it.timeMs } / earlier.size,
        bins = bins,
        youBin = binOf(current.timeMs),
        beaten = beaten,
        best = beaten == earlier.size,
        rank = 1 + ahead,
    )
}
