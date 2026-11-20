package com.ivandsky.puzzlebox.insights.recap

import com.ivandsky.puzzlebox.insights.domain.recap.DayTally
import com.ivandsky.puzzlebox.insights.domain.recap.FriendsStanding
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.insights.domain.recap.RecapPeriod
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import java.time.YearMonth

/** The cards and names the recap screen tests and renders share. */
internal object RecapFixtures {
    val zip = GameId("zip")
    val queens = GameId("queens")
    val sudoku = GameId("sudoku")

    private val names = mapOf(zip to "Zip", queens to "Queens", sudoku to "Sudoku")

    val gameName: (GameId) -> String = { names.getValue(it) }

    val november = RecapPeriod.Month(YearMonth.of(2026, 11))
    val year2026 = RecapPeriod.Year(2026)
    val year2025 = RecapPeriod.Year(2025)

    val solved = RecapCard.Solved(
        total = 42,
        perGame = listOf(
            RecapCard.Solved.PerGame(zip, 18),
            RecapCard.Solved.PerGame(queens, 15),
            RecapCard.Solved.PerGame(sudoku, 9),
        ),
        hintFree = 36,
        longestHintFreeRun = 9,
    )

    /** A second page so a story has somewhere to go; the later tickets' cards stand here until they land. */
    val secondSolved = RecapCard.Solved(total = 7, perGame = listOf(RecapCard.Solved.PerGame(zip, 7)))

    /** Fastest solves: two games with a replay, one imported time without. */
    val fastest = RecapCard.Fastest(
        rows = listOf(
            RecapCard.Fastest.Row(zip, timeMs = 58_000, day = LocalDate.of(2026, 11, 3), puzzleNumber = 212, hasReplay = true, averageMs = 91_000, solves = 6),
            RecapCard.Fastest.Row(queens, timeMs = 141_000, day = LocalDate.of(2026, 11, 12), puzzleNumber = 190, hasReplay = true, averageMs = 215_000, solves = 4),
            RecapCard.Fastest.Row(sudoku, timeMs = 312_000, day = LocalDate.of(2026, 11, 8), puzzleNumber = 77, hasReplay = false, averageMs = null, solves = 1),
        ),
    )

    /** A 17-day streak, two of its days held by freezes. */
    val streak = RecapCard.Streak(
        days = 17, from = LocalDate.of(2026, 11, 3), to = LocalDate.of(2026, 11, 19), freezesSpent = 2,
        frozenDays = setOf(LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 14)), span = november,
    )

    /** Every local Moment kind came up, two streak milestones among them. */
    val moments = RecapCard.Moments(personalBests = 5, firstSubX = 2, fullSets = 14, streakMilestones = listOf(7, 30))

    /** Three hours and a bit over 42 solves. */
    val totalTime = RecapCard.TotalTime(
        totalMs = 11_535_000,
        solved = 42,
        days = listOf(DayTally(LocalDate.of(2026, 11, 3), solved = 42, timeMs = 11_535_000, hintFree = 36)),
        period = november,
    )

    /** The friends read is still out: the card holds its place. */
    val friendsReading = RecapCard.Friends(FriendsStanding.Reading)

    /** Queens earned the most points among friends. */
    val friendsLoaded = RecapCard.Friends(FriendsStanding.Loaded(queens, points = 1_340.0))

    /** Day 2 of December: November's friends results settle on the 4th. */
    val friendsSettling = RecapCard.Friends(FriendsStanding.Settling(LocalDate.of(2026, 12, 4)))

    /** The friends read could not finish. */
    val friendsUnavailable = RecapCard.Friends(FriendsStanding.Unavailable)

    /** A year of play: March, its busiest month, and the month a streak began. */
    val march = RecapCard.Month(YearMonth.of(2026, 3), solved = 42, fastestGame = zip, fastestTimeMs = 58_000)
    val april = RecapCard.Month(YearMonth.of(2026, 4), solved = 9, fastestGame = queens, fastestTimeMs = 141_000)

    /** A year built from imported results alone: 312 puzzle days. */
    val solvedImported = solved.copy(importedDays = 312)

    /** A year's best game among friends from six kept weeks. */
    val friendsLoadedYear = RecapCard.Friends(FriendsStanding.Loaded(queens, points = 212.0, weeks = 6))

    val ready = RecapUiState.Ready(period = november, title = "November 2026", cards = listOf(solved, secondSolved), initialPage = 0)
}

/** A ready recap over [cards], opening on the first. */
internal fun readyOf(vararg cards: RecapCard) =
    RecapUiState.Ready(period = RecapFixtures.november, title = "November 2026", cards = cards.toList(), initialPage = 0)

/** A ready yearly recap titled [title] ("2026 so far", "2025") over [cards], opening on the first. */
internal fun readyYear(title: String, vararg cards: RecapCard) =
    RecapUiState.Ready(period = RecapFixtures.year2026, title = title, cards = cards.toList(), initialPage = 0)
