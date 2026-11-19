package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import kotlin.math.ceil

/**
 * What the Results histogram and caption need for one scope (every day, or one weekday), from
 * its earlier solves plus this one. Bins are over time for every game; [beaten], [best] and
 * [rank] follow the game's [RankOrder].
 *
 * @property solves earlier solves plus this one.
 * @property averageEarlierMs the earlier solves' average time; null on a first solve.
 * @property bins contiguous bins of round-time edges that hold every solve; empty slots stay. The last
 *   may be open ([SpreadBin.open]) and hold every slower solve.
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
private val NiceStepsMs = NiceStepsSeconds.map { it * 1_000L }
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

/** Below this many solves a slow tail says little, so the chart keeps the even bins. */
private const val SLOW_TAIL_MIN_SOLVES = 10
private const val SLOW_TAIL_PERCENTILE = 0.9

/** Where the equal-width bins start, how wide they are and how many there are, a last open one included. */
private class BinLayout(val stepMs: Long, val first: Long, val count: Int, val openLast: Boolean)

/** Equal bins from the fastest to the slowest time with the smallest nice step that fits [target]. */
private fun evenLayout(min: Long, max: Long, target: Int): BinLayout {
    val floorStepMs = if (min == max) min / SINGLE_BAR_THIRDS else 0L
    val stepMs = NiceStepsMs.firstOrNull { it >= floorStepMs && max / it - min / it + 1 <= target } ?: NiceStepsMs.last()
    return BinLayout(stepMs, min / stepMs, (max / stepMs - min / stepMs + 1).toInt(), openLast = false)
}

/**
 * Equal bins from the fastest time to the 90th-percentile time, rounded up to a bin edge, and one
 * open last bin for every solve slower than that edge; null when none is, so no tail is drawn. The
 * caller drops it when its step is no finer than the even bins', since then nothing is squashed.
 */
private fun slowTailLayout(sorted: List<Long>, target: Int): BinLayout? {
    val min = sorted.first()
    val usual = sorted[ceil(sorted.size * SLOW_TAIL_PERCENTILE).toInt() - 1]
    val stepMs = NiceStepsMs.firstOrNull { usual / it - min / it + 1 <= target - 1 } ?: NiceStepsMs.last()
    val first = min / stepMs
    val main = (usual / stepMs - first + 1).toInt()
    if (sorted.last() < (first + main) * stepMs) return null
    return BinLayout(stepMs, first, main + 1, openLast = true)
}

/**
 * Bins [earlier] plus [current] times with the smallest nice step that fits [targetBins], and
 * counts how many of [earlier] [current] beats under [order]. When every time is the same the
 * step is at least a third of it, so the one bar does not sit in a sliver range. With at least
 * [SLOW_TAIL_MIN_SOLVES] solves, a few slow ones do not stretch the axis: they share one open
 * last bin ([SpreadBin.open]). Rank, beaten and the average always use every solve.
 */
fun timeSpread(earlier: List<BestScore>, current: BestScore, order: RankOrder): TimeSpread {
    val beaten = earlier.count { order.comparator.compare(current, it) < 0 }
    val ahead = earlier.count { order.comparator.compare(it, current) < 0 }
    val times = earlier.map { it.timeMs } + current.timeMs
    val min = times.min()
    val max = times.max()
    val target = targetBins(times.size)
    val even = evenLayout(min, max, target)
    val tail = if (times.size >= SLOW_TAIL_MIN_SOLVES) slowTailLayout(times.sorted(), target) else null
    val layout = tail?.takeIf { it.stepMs < even.stepMs } ?: even
    fun binOf(timeMs: Long): Int = (timeMs / layout.stepMs - layout.first).toInt().coerceAtMost(layout.count - 1)

    val counts = IntArray(layout.count)
    times.forEach { counts[binOf(it)]++ }
    val plot = maxOf(counts.max(), MIN_TALLEST)
    val bins = counts.mapIndexed { index, n ->
        SpreadBin(
            count = n,
            height = if (n == 0) 0f else maxOf(MIN_BAR_HEIGHT, n.toFloat() / plot),
            fromMs = (layout.first + index) * layout.stepMs,
            toMs = (layout.first + index + 1) * layout.stepMs,
            open = layout.openLast && index == layout.count - 1,
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
