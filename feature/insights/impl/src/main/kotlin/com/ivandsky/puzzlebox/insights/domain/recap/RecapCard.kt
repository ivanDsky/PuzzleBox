package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import java.time.YearMonth

/**
 * One card of a recap: a closed set, so the feature's one card-to-page `when` stays exhaustive. A new card is a new
 * provider, a new variant here and its page.
 */
internal sealed interface RecapCard {

    /**
     * Puzzles solved: the period's [total] and the [perGame] split, in the app's game order. [importedDays] is set only
     * for a year built from imported results alone: the puzzle days it covers, which the page says (A22). [hintFree] of
     * the [total] needed no hint, and [longestHintFreeRun] is the longest stretch of them in a row (null when there is
     * none to report); the page shows both as a stripe only when [hintFree] is above 0.
     */
    data class Solved(
        val total: Int,
        val perGame: List<PerGame>,
        val importedDays: Int? = null,
        val hintFree: Int = 0,
        val longestHintFreeRun: Int? = null,
    ) : RecapCard {
        data class PerGame(val game: GameId, val solved: Int)
    }

    /** Fastest solve: one [Row] per game played, in the app's game order. */
    data class Fastest(val rows: List<Row>) : RecapCard {

        /**
         * [game]'s quickest solve in the period: its time, the puzzle [day] and [puzzleNumber], and whether there is a replay to
         * watch (an import has none). [solves] counts the game's solves in the period and [averageMs] is their average, null
         * when there is one solve or the average is not above [timeMs]: then there is no average to beat.
         */
        data class Row(
            val game: GameId,
            val timeMs: Long,
            val day: LocalDate,
            val puzzleNumber: Int?,
            val hasReplay: Boolean,
            val averageMs: Long?,
            val solves: Int,
        )
    }

    /**
     * One month of a year (A3): [solved] counted solves in [month], and its quickest, [fastestTimeMs] in [fastestGame].
     * A year's story has one of these for each month with a counted solve, after the totals; a month's story has none.
     */
    data class Month(val month: YearMonth, val solved: Int, val fastestGame: GameId, val fastestTimeMs: Long) : RecapCard

    /**
     * Longest streak: [days] in a row, frozen days included, over [from]..[to] inside the period (a streak across the
     * period's edge shows only its own side), and the days [freezesSpent] holding streaks anywhere in the period.
     */
    data class Streak(val days: Int, val from: LocalDate, val to: LocalDate, val freezesSpent: Int) : RecapCard

    /**
     * Moments earned, local kinds only (A6): [personalBests] and [firstSubX] and [fullSets] counted, and every
     * [streakMilestones] reached (7, 30 or 100 days), oldest first. Friend Moments are not counted.
     */
    data class Moments(
        val personalBests: Int,
        val firstSubX: Int,
        val fullSets: Int,
        val streakMilestones: List<Int>,
    ) : RecapCard {
        val total: Int get() = personalBests + firstSubX + fullSets + streakMilestones.size
    }

    /**
     * Best game among friends: what the friends read of the period came to, as a [state] the card shows. Its place is
     * fixed before the read answers ([FriendsStanding.Reading]), and the answer settles it in that place (A16).
     */
    data class Friends(val state: FriendsStanding.Shown) : RecapCard

    /**
     * Total time spent solving: [totalMs] over [solved] counted solves, pauses excluded, imports included (A8). [days]
     * are the period's per-day tallies, which add up to [totalMs]; the day river draws them across [period].
     */
    data class TotalTime(val totalMs: Long, val solved: Int, val days: List<DayTally>, val period: RecapPeriod) : RecapCard
}
