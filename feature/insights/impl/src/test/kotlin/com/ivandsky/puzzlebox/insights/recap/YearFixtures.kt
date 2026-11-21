package com.ivandsky.puzzlebox.insights.recap

import com.ivandsky.puzzlebox.insights.domain.recap.MonthHighlight
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.model.GameId
import java.time.YearMonth

/** The year cards' contract states: a full 2026 of twelve months (625 puzzles, October busiest), a year under way, imports alone, and a single month. */
internal object YearFixtures {
    private val queens = GameId("queens")
    private val tango = GameId("tango")
    private val zip = GameId("zip")
    private val sudoku = GameId("sudoku")

    private val counts = listOf(24, 31, 38, 36, 41, 39, 46, 52, 64, 96, 88, 70)
    private val games = listOf(queens, zip, tango, zip, queens, zip, tango, zip, sudoku, zip, zip, queens)
    private val times = listOf(61_000L, 55_000, 49_000, 44_000, 40_000, 38_000, 33_000, 19_000, 69_000, 21_000, 22_000, 50_000)

    private val under = listOf(12, 8, 15, 22, 9, 17, 30, 25, 6, 31, 18, 0)

    private fun month(number: Int) = YearMonth.of(2026, number)

    /** A month card whose quickest solve is its best against the average, and which leads with what most of it was. */
    private fun card(month: YearMonth, solved: Int, game: GameId, timeMs: Long, underAveragePercent: Int) = RecapCard.Month(
        month, solved, game, timeMs, underAveragePercent,
        fastestGame = game, fastestTimeMs = timeMs, lead = MonthHighlight.Focus(game, solved, solved), also = null, moments = emptyList(),
    )

    /** Month cards for the first [months] months of 2026 (September's quickest solve is Mini Sudoku's). */
    fun monthCards(months: Int = 12): List<RecapCard.Month> =
        (1..months).map { card(month(it), counts[it - 1], games[it - 1], times[it - 1], under[it - 1]) }

    /** The finale of the first [months] months; the rest are empty tiles. */
    fun finale(months: Int = 12, total: Int = 625, importedDays: Int? = null) = RecapCard.YearFinale(
        total = total,
        months = (1..12).map { RecapCard.YearFinale.Tile(month(it), if (it <= months) counts[it - 1] else 0, games[it - 1].takeIf { _ -> it <= months }) },
        bestMonth = month(10),
        bestMonthSolved = 96,
        fastestGame = zip,
        fastestTimeMs = 19_000,
        fastestMonth = month(8),
        mostPlayedGame = queens,
        mostPlayedSolved = 196,
        importedDays = importedDays,
    )

    private val totals = listOf(RecapFixtures.fastest, RecapFixtures.streak, RecapFixtures.moments, RecapFixtures.friendsLoadedYear, RecapFixtures.totalTime)

    /** The deck of a year: the totals, a month card per month, the finale. A year of imports has no friends card. */
    fun deck(months: List<RecapCard.Month>, finale: RecapCard.YearFinale, imported: Boolean = false): List<RecapCard> =
        listOf<RecapCard>(if (imported) RecapFixtures.solvedImported else RecapFixtures.solved) +
            totals.filterNot { imported && it === RecapFixtures.friendsLoadedYear } + months + finale

    val oneMonth = card(month(10), solved = 1, game = zip, timeMs = 21_000, underAveragePercent = 0)
}
