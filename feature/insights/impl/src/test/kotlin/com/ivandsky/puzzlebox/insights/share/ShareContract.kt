package com.ivandsky.puzzlebox.insights.share

import com.ivandsky.puzzlebox.insights.domain.recap.DayTally
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.insights.domain.recap.RecapPeriod
import com.ivandsky.puzzlebox.insights.recap.MomentsFixtures
import com.ivandsky.puzzlebox.insights.recap.SolvedFixtures
import java.time.LocalDate
import java.time.YearMonth

/** The contract's share datasets (default, longest, empty) built from its numbers, and a year and two games, for the poster and Share page goldens. */
internal object ShareContract {
    private val october = RecapPeriod.Month(YearMonth.of(2026, 10))
    private fun time(ms: Long, solved: Int) = RecapCard.TotalTime(ms, solved, listOf(DayTally(LocalDate.of(2026, 10, 3), solved, ms, 0)), october)
    private fun streak(days: Int, freezes: Int) = RecapCard.Streak(days, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, days), freezes, emptySet(), october)

    /** 96 solved over five games, 71 without a hint, 23-day streak, 6 Moments, 3h 47m. */
    val default = listOf(SolvedFixtures.default, streak(23, 2), MomentsFixtures.withoutGames(MomentsFixtures.default), time(13_650_000, 96))

    /** 155 solved (31 a game), 31-day streak, 14 Moments, 99h 59m. */
    val longest = listOf(SolvedFixtures.longest, streak(31, 4), MomentsFixtures.withoutGames(MomentsFixtures.longest), time(359_940_000, 155))

    /** One Queens solve: a 1-day streak, no Moments, 0h 1m. */
    val empty = listOf(SolvedFixtures.empty, streak(1, 0), time(72_000, 1))

    /** A year: 1067 solved over five games (Strands only 6), 902 without a hint, a 23-day streak, 14 Moments, 64h 12m. */
    val year = listOf(
        SolvedFixtures.year,
        RecapCard.Streak(23, LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 24), 2, emptySet(), RecapPeriod.Year(2026)),
        MomentsFixtures.withoutGames(MomentsFixtures.longest),
        RecapCard.TotalTime(231_120_000, 1067, listOf(DayTally(LocalDate.of(2026, 3, 3), 3, 600_000, 0)), RecapPeriod.Year(2026)),
    )

    /** 58 solved over two games (Queens 40, Zip 18), a 9-day streak, 6 Moments, 2h 5m. */
    val twoGames = listOf(SolvedFixtures.twoGames, streak(9, 1), MomentsFixtures.withoutGames(MomentsFixtures.default), time(7_500_000, 58))

    const val LONG_NAME = "Maximiliana Oyelaran-Featherstonehaugh"
}
