package com.ivandsky.puzzlebox.insights.recap

import com.ivandsky.puzzlebox.insights.domain.recap.EarnedMoment
import com.ivandsky.puzzlebox.insights.domain.recap.EarnedMoment.Kind
import com.ivandsky.puzzlebox.insights.domain.recap.MonthHighlight
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import java.time.LocalDate
import java.time.YearMonth

/** Month cards with their highlights and stamps: a rich January, a quiet month with no stamps, and the longest copy. */
internal object MonthFixtures {
    private val zip = RecapFixtures.zip
    private val queens = RecapFixtures.queens
    private val sudoku = RecapFixtures.sudoku

    private fun jan(day: Int) = LocalDate.of(2026, 1, day)
    private fun sep(day: Int) = LocalDate.of(2026, 9, day)

    /** January: a Queens best on the 14th, fastest all year, also a 7-day streak, and four stamps. */
    val january = RecapCard.Month(
        YearMonth.of(2026, 1), solved = 96, bestGame = zip, bestTimeMs = 58_000, underAveragePercent = 31,
        fastestGame = zip, fastestTimeMs = 11_000,
        lead = MonthHighlight.PersonalBest(queens, jan(14), timeMs = 74_000, fastestAllYear = true),
        also = MonthHighlight.Streak(jan(7), days = 7),
        moments = listOf(
            EarnedMoment(Kind.Streak, jan(7), streakDays = 7),
            EarnedMoment(Kind.PersonalBest, jan(14), queens),
            EarnedMoment(Kind.FirstSubX, jan(19), zip, thresholdMs = 10_000),
            EarnedMoment(Kind.FullSet, jan(24)),
        ),
    )

    /** October: a single solve and no stamps. */
    val quiet = RecapCard.Month(
        YearMonth.of(2026, 10), solved = 1, bestGame = zip, bestTimeMs = 141_000, underAveragePercent = 0,
        fastestGame = zip, fastestTimeMs = 141_000, lead = MonthHighlight.QuietMonth(solved = 1, mostlyGame = zip), also = null, moments = emptyList(),
    )

    /** September, the longest copy: 1,240 solves, an hour-long Sudoku best and a 100-day streak among four stamps. */
    val longest = RecapCard.Month(
        YearMonth.of(2026, 9), solved = 1_240, bestGame = sudoku, bestTimeMs = 3_723_000, underAveragePercent = 12,
        fastestGame = sudoku, fastestTimeMs = 3_723_000,
        lead = MonthHighlight.PersonalBest(sudoku, sep(21), timeMs = 3_723_000, fastestAllYear = false),
        also = MonthHighlight.FirstSubX(zip, sep(3), thresholdMs = 10_000),
        moments = listOf(
            EarnedMoment(Kind.FirstSubX, sep(3), zip, thresholdMs = 10_000),
            EarnedMoment(Kind.FullSet, sep(12)),
            EarnedMoment(Kind.PersonalBest, sep(21), sudoku),
            EarnedMoment(Kind.Streak, sep(30), streakDays = 100),
        ),
    )
}
