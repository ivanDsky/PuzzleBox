package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import kotlin.math.roundToLong

/**
 * What the Results histogram and caption need, from a game's earlier solves plus this one.
 * Bins are over time for every game; [beaten] and [best] follow the game's [RankOrder].
 *
 * @property solves earlier solves plus this one.
 * @property averageEarlierMs the earlier solves' average time; null on a first solve.
 * @property bins [BIN_COUNT] bins from [loMs] to [hiMs]; empty on a first solve.
 * @property youBin the bin holding this solve; null on a first solve.
 * @property beaten earlier solves this one beats by the rank order; ties are not beaten.
 * @property best this solve beats every earlier one.
 */
data class TimeSpread(
    val solves: Int,
    val averageEarlierMs: Long?,
    val bins: List<SpreadBin>,
    val youBin: Int?,
    val loMs: Long,
    val hiMs: Long,
    val beaten: Int,
    val best: Boolean,
) {
    val isFirstSolve: Boolean get() = bins.isEmpty()
}

const val BIN_COUNT = 10
private const val LOW_MARGIN = 0.97
private const val HIGH_MARGIN = 1.03
private const val MIN_BAR_HEIGHT = 0.06f

/**
 * Bins [earlier] plus [current] times into [BIN_COUNT] bins from `min * 0.97` to `max * 1.03`,
 * and counts how many of [earlier] [current] beats under [order].
 */
fun timeSpread(earlier: List<BestScore>, current: BestScore, order: RankOrder): TimeSpread {
    if (earlier.isEmpty()) {
        return TimeSpread(
            solves = 1, averageEarlierMs = null, bins = emptyList(), youBin = null,
            loMs = 0L, hiMs = 0L, beaten = 0, best = true,
        )
    }
    val beaten = earlier.count { order.comparator.compare(current, it) < 0 }
    val times = earlier.map { it.timeMs } + current.timeMs
    val lo = times.min() * LOW_MARGIN
    val hi = times.max() * HIGH_MARGIN
    val span = hi - lo
    fun binOf(timeMs: Long): Int =
        if (span <= 0.0) 0 else ((timeMs - lo) / span * BIN_COUNT).toInt().coerceIn(0, BIN_COUNT - 1)

    val counts = IntArray(BIN_COUNT)
    times.forEach { counts[binOf(it)]++ }
    val tallest = counts.max()
    val bins = counts.map { count ->
        SpreadBin(count, if (count == 0) 0f else maxOf(MIN_BAR_HEIGHT, count.toFloat() / tallest))
    }
    return TimeSpread(
        solves = times.size,
        averageEarlierMs = earlier.sumOf { it.timeMs } / earlier.size,
        bins = bins,
        youBin = binOf(current.timeMs),
        loMs = lo.roundToLong(),
        hiMs = hi.roundToLong(),
        beaten = beaten,
        best = beaten == earlier.size,
    )
}
