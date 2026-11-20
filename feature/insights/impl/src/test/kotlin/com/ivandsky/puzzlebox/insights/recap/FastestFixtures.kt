package com.ivandsky.puzzlebox.insights.recap

import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

/** The October 2026 dataset the Fastest card's references are drawn from (contract-shared.md, corrected). */
internal object FastestFixtures {
    val queens = GameId("queens")
    val tango = GameId("tango")
    val zip = GameId("zip")
    val miniSudoku = GameId("mini-sudoku")
    val strands = GameId("strands")

    val gameName: (GameId) -> String = {
        mapOf(queens to "Queens", tango to "Tango", zip to "Zip", miniSudoku to "Mini Sudoku", strands to "Strands", RecapFixtures.sudoku to "Sudoku").getValue(it)
    }

    val accentOf: (GameId) -> AccentSlot = {
        mapOf(queens to AccentSlot.Violet, tango to AccentSlot.Orange, zip to AccentSlot.Blue, miniSudoku to AccentSlot.Teal, strands to AccentSlot.Pink)
            .getValue(it)
    }

    private fun oct(day: Int) = LocalDate.of(2026, 10, day)

    private val queensRow = RecapCard.Fastest.Row(queens, 48_000, oct(17), 386, hasReplay = true, averageMs = 136_000, solves = 28)
    private val tangoRow = RecapCard.Fastest.Row(tango, 62_000, oct(9), null, hasReplay = true, averageMs = 156_000, solves = 24)
    private val zipRow = RecapCard.Fastest.Row(zip, 21_000, oct(22), null, hasReplay = true, averageMs = 58_000, solves = 22)
    private val miniSudokuRow = RecapCard.Fastest.Row(miniSudoku, 75_000, oct(3), null, hasReplay = false, averageMs = 183_000, solves = 14)
    private val strandsRow = RecapCard.Fastest.Row(strands, 160_000, oct(28), 320, hasReplay = true, averageMs = 277_000, solves = 8)

    val default = RecapCard.Fastest(listOf(queensRow, tangoRow, zipRow, miniSudokuRow, strandsRow))

    /** Strands has one solve: its card has no average to beat. */
    val noAverage = RecapCard.Fastest(default.rows.dropLast(1) + strandsRow.copy(averageMs = null, solves = 1))

    val singleGame = RecapCard.Fastest(listOf(queensRow))

    /** The widest times the card sets: a seven-column average, an hour-long imported solve, and the shortest solves. */
    val longest = RecapCard.Fastest(
        listOf(
            queensRow.copy(timeMs = 7_000), tangoRow.copy(timeMs = 11_000), zipRow.copy(timeMs = 9_000),
            strandsRow.copy(timeMs = 34_000),
            miniSudokuRow.copy(timeMs = 3_599_000, averageMs = 3_730_000, solves = 31),
        ),
    )

    /** The recap story round a Fastest card, opening on it with a card before and cards after. */
    fun story(card: RecapCard.Fastest): RecapUiState.Ready {
        val others = listOf(RecapFixtures.streak, RecapFixtures.secondSolved, RecapFixtures.streak, RecapFixtures.secondSolved, RecapFixtures.streak)
        return RecapUiState.Ready(
            period = RecapFixtures.november, title = "October 2026",
            cards = listOf(RecapFixtures.solved, card) + others, initialPage = 1,
        )
    }
}
