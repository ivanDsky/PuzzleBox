package com.ivandsky.puzzlebox.insights.recap

import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.insights.domain.recap.RecapPeriod
import com.ivandsky.puzzlebox.model.GameId
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

    val ready = RecapUiState.Ready(period = november, cards = listOf(solved, secondSolved), initialPage = 0)
}
