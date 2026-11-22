package com.ivandsky.puzzlebox.insights.recap

import com.ivandsky.puzzlebox.insights.domain.recap.MonthHighlight
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import java.time.YearMonth

private val March = RecapCard.Month(
    YearMonth.of(2026, 3), solved = 42, bestGame = RecapFixtures.zip, bestTimeMs = 58_000, underAveragePercent = 31,
    fastestGame = RecapFixtures.zip, fastestTimeMs = 58_000, lead = MonthHighlight.Focus(RecapFixtures.zip, 42, 42), also = null, moments = emptyList(),
)

private val April = RecapCard.Month(
    YearMonth.of(2026, 4), solved = 9, bestGame = RecapFixtures.queens, bestTimeMs = 141_000, underAveragePercent = 12,
    fastestGame = RecapFixtures.queens, fastestTimeMs = 141_000, lead = MonthHighlight.Focus(RecapFixtures.queens, 9, 9), also = null, moments = emptyList(),
)

/** A year of play: March, its busiest month. */
internal val RecapFixtures.march: RecapCard.Month get() = March

/** A year of play: April, the month a streak began. */
internal val RecapFixtures.april: RecapCard.Month get() = April
