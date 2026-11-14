package com.ivandsky.puzzlebox.domain

/**
 * What the Results page shows beside the time for one solve: the streak in days over every
 * game, the [WEEK_DAYS] days ending today (oldest first), and the [spread] of this game's
 * times over every day plus the same [weekday]'s own spread. [averageMs] is the average of
 * the game's earlier solves; null on a first solve. [freezes] is null while the StreakFreeze flag is
 * off. [moment] is set only when this very solve spent or earned a freeze, so Result shows it once.
 */
data class ResultInsights(
    val streak: Int,
    val week: List<ResultDay>,
    val spread: TimeSpread,
    val weekday: WeekdaySpread,
    val freezes: StreakFreezes? = null,
    val moment: StreakFreezes.Moment? = null,
) {
    val averageMs: Long? get() = spread.averageEarlierMs

    /** The scope the chart opens on: the weekday once it has [WEEKDAY_MIN_EARLIER] earlier solves. */
    val defaultScope: SpreadScope
        get() = if (weekday.spread.solves > WEEKDAY_MIN_EARLIER) SpreadScope.Weekday else SpreadScope.All
}

const val WEEK_DAYS = 7

/** Earlier same-weekday solves needed before the weekday is the default scope or a ranked weekday. */
const val WEEKDAY_MIN_EARLIER = 2
