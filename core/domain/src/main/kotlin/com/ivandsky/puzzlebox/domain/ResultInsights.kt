package com.ivandsky.puzzlebox.domain

/**
 * What the Results page shows beside the time for one solve: the streak in days over every
 * game, the [WEEK_DAYS] days ending today (oldest first), and the [spread] of this game's
 * times. [averageMs] is the average of the game's earlier solves; null on a first solve.
 */
data class ResultInsights(
    val streak: Int,
    val week: List<ResultDay>,
    val spread: TimeSpread,
) {
    val averageMs: Long? get() = spread.averageEarlierMs
}

const val WEEK_DAYS = 7
