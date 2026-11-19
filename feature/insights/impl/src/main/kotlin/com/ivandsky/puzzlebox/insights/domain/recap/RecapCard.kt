package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

/**
 * One card of a recap: a closed set, so the feature's one card-to-page `when` stays exhaustive. A new card is a new
 * provider, a new variant here and its page.
 */
internal sealed interface RecapCard {

    /** Puzzles solved: the period's [total] and the [perGame] split, in the app's game order. */
    data class Solved(val total: Int, val perGame: List<PerGame>) : RecapCard {
        data class PerGame(val game: GameId, val solved: Int)
    }

    /** Fastest solve: one [Row] per game played, in the app's game order. */
    data class Fastest(val rows: List<Row>) : RecapCard {

        /** [game]'s quickest solve in the period: its time, the puzzle [day] and [puzzleNumber], and whether there is a replay to watch (an import has none). */
        data class Row(val game: GameId, val timeMs: Long, val day: LocalDate, val puzzleNumber: Int?, val hasReplay: Boolean)
    }

    /**
     * Longest streak: [days] in a row, frozen days included, over [from]..[to] inside the period (a streak across the
     * period's edge shows only its own side), and the days [freezesSpent] holding streaks anywhere in the period.
     */
    data class Streak(val days: Int, val from: LocalDate, val to: LocalDate, val freezesSpent: Int) : RecapCard

    /** Hint-free solves: [hintFree] of the period's [solved] counted solves needed no hint. */
    data class HintFree(val hintFree: Int, val solved: Int) : RecapCard

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

    /** Total time spent solving: [totalMs] over [solved] counted solves, pauses excluded, imports included (A8). */
    data class TotalTime(val totalMs: Long, val solved: Int) : RecapCard
}
