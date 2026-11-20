package com.ivandsky.puzzlebox.insights.recap

import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.insights.domain.recap.RecapPeriod
import java.time.LocalDate
import java.time.YearMonth

/** The four Streak states of the contract: October 2026's default, one without freezes, the longest, and 2026 so far. */
internal object StreakFixtures {
    private val october = RecapPeriod.Month(YearMonth.of(2026, 10))

    private fun oct(day: Int) = LocalDate.of(2026, 10, day)

    /** 23 days, Oct 6 – Oct 28; two freezes held it. */
    val default = RecapCard.Streak(
        days = 23, from = oct(6), to = oct(28), freezesSpent = 2, frozenDays = setOf(oct(14), oct(21)), span = october,
    )

    /** The same run with no freeze spent. */
    val noFreezes = default.copy(freezesSpent = 0, frozenDays = emptySet())

    /** Every day of October, four of them frozen. */
    val longest = RecapCard.Streak(
        days = 31, from = oct(1), to = oct(31), freezesSpent = 4,
        frozenDays = setOf(oct(6), oct(13), oct(14), oct(22)), span = october,
    )

    /** 74 days, Jun 24 – Sep 5, in 2026 so far as of Dec 20 (the last two weeks are to come); three freezes. */
    val year = RecapCard.Streak(
        days = 74, from = LocalDate.of(2026, 6, 24), to = LocalDate.of(2026, 9, 5), freezesSpent = 3,
        frozenDays = setOf(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 15), LocalDate.of(2026, 8, 5)),
        span = RecapPeriod.Year(2026), today = LocalDate.of(2026, 12, 20),
    )
}
