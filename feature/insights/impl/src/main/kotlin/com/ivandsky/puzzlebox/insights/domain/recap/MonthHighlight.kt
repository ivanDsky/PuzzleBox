package com.ivandsky.puzzlebox.insights.domain.recap

import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

/**
 * One thing a month of a year did that its card can lead with ([SpotlightMonths] picks): a Moment it earned, a game that
 * ran well under its year average or was played more than usual, its busiest day, a quiet month, or what most of it was.
 * [game] is the game it is about, null when it is about no one game; [day] is the puzzle day it happened on, null when it
 * took the whole month.
 */
internal sealed interface MonthHighlight {
    val game: GameId? get() = null
    val day: LocalDate? get() = null

    /**
     * A personal best in [game] on [day]. [timeMs] is its time when it was the game's quickest solve of the month, null
     * otherwise (a game ranked by hints first can set a best with fewer hints and a slower time). [fastestAllYear] when it
     * was the game's quickest solve of the year.
     */
    data class PersonalBest(override val game: GameId, override val day: LocalDate, val timeMs: Long?, val fastestAllYear: Boolean) : MonthHighlight

    /** The first solve of [game] under [thresholdMs], on [day]. */
    data class FirstSubX(override val game: GameId, override val day: LocalDate, val thresholdMs: Long) : MonthHighlight

    /** A streak reaching [days] days in a row on [day]. */
    data class Streak(override val day: LocalDate, val days: Int) : MonthHighlight

    /** [count] full sets in the month, the last of them on [day]. */
    data class FullSets(override val day: LocalDate, val count: Int) : MonthHighlight

    /** [game]'s best time of the month, [bestMs], ran [percent] percent under its average over the year, [averageMs]. */
    data class UnderAverage(override val game: GameId, val percent: Int, val bestMs: Long, val averageMs: Long) : MonthHighlight

    /** The month [game] was played most: [solved] puzzles, against [usualSolved] in its usual month. */
    data class BusiestMonth(override val game: GameId, val solved: Int, val usualSolved: Int) : MonthHighlight

    /** The month's busiest [day], with [solved] puzzles; of equal days, the earlier. */
    data class BusiestDay(override val day: LocalDate, val solved: Int) : MonthHighlight

    /** A month far under the year's usual: [solved] puzzles, most of them [mostlyGame]. */
    data class QuietMonth(val solved: Int, val mostlyGame: GameId) : MonthHighlight

    /** What a month with nothing else to say was: [solved] of its [of] puzzles were [game], all of them when the two match. */
    data class Focus(override val game: GameId, val solved: Int, val of: Int) : MonthHighlight
}
