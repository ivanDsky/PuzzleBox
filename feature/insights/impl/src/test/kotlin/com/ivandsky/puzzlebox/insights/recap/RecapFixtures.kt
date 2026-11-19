package com.ivandsky.puzzlebox.insights.recap

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

    val solved = RecapCard.Solved(
        total = 42,
        perGame = listOf(
            RecapCard.Solved.PerGame(zip, 18),
            RecapCard.Solved.PerGame(queens, 15),
            RecapCard.Solved.PerGame(sudoku, 9),
        ),
    )

    /** A second page so a story has somewhere to go; the later tickets' cards stand here until they land. */
    val secondSolved = RecapCard.Solved(total = 7, perGame = listOf(RecapCard.Solved.PerGame(zip, 7)))

    /** Fastest solves: two games with a replay, one imported time without. */
    val fastest = RecapCard.Fastest(
        rows = listOf(
            RecapCard.Fastest.Row(zip, timeMs = 58_000, day = LocalDate.of(2026, 11, 3), puzzleNumber = 212, hasReplay = true),
            RecapCard.Fastest.Row(queens, timeMs = 141_000, day = LocalDate.of(2026, 11, 12), puzzleNumber = 190, hasReplay = true),
            RecapCard.Fastest.Row(sudoku, timeMs = 312_000, day = LocalDate.of(2026, 11, 8), puzzleNumber = 77, hasReplay = false),
        ),
    )

    /** A 17-day streak, two of its days held by freezes. */
    val streak = RecapCard.Streak(days = 17, from = LocalDate.of(2026, 11, 3), to = LocalDate.of(2026, 11, 19), freezesSpent = 2)

    val ready = RecapUiState.Ready(period = november, cards = listOf(solved, secondSolved), initialPage = 0)
}

/** A ready recap over [cards], opening on the first. */
internal fun readyOf(vararg cards: RecapCard) =
    RecapUiState.Ready(period = RecapFixtures.november, cards = cards.toList(), initialPage = 0)
