package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.model.GameId
import java.time.YearMonth

// The month-of-the-year page and its tablet list fact, light and dark: a busy month, and a month with a single solve. A preview never animates, so the count shows its end value.

private val Zip = GameId("zip")
private val Queens = GameId("queens")

private val Names = mapOf(Zip to "Zip", Queens to "Queens")

private fun name(game: GameId): String = Names.getValue(game)

private val March = RecapCard.Month(YearMonth.of(2026, 3), solved = 42, bestGame = Zip, bestTimeMs = 58_000, underAveragePercent = 31)

private val OneSolve = RecapCard.Month(YearMonth.of(2026, 9), solved = 1, bestGame = Queens, bestTimeMs = 141_000, underAveragePercent = 0)

@PuzzleBoxPreviews
@Composable
private fun MonthCardPagePreview() = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) { MonthCardPage(March, "2026 so far", ::name, place = MonthPlace(3, 12, "April")) }
}

@PuzzleBoxPreviews
@Composable
private fun MonthCardPageOneSolvePreview() = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) { MonthCardPage(OneSolve, "2026", ::name, place = MonthPlace(1, 1, "Year finale")) }
}

@PuzzleBoxPreviews
@Composable
private fun MonthCardFactPreview() = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(Spacing.lg)) { MonthCardFact(March, ::name) }
    }
}

private val Finale = RecapCard.YearFinale(
    total = 625,
    months = (1..12).map { RecapCard.YearFinale.Tile(YearMonth.of(2026, it), solved = if (it > 10) 0 else it * 10, game = if (it > 10) null else Zip) },
    bestMonth = YearMonth.of(2026, 10),
    bestMonthSolved = 100,
    fastestGame = Zip,
    fastestTimeMs = 19_000,
    fastestMonth = YearMonth.of(2026, 8),
    mostPlayedGame = Queens,
    mostPlayedSolved = 196,
)

@PuzzleBoxPreviews
@Composable
private fun YearFinalePagePreview() = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) { YearFinalePage(Finale, "2026 so far", ::name, "Ivan", onShare = {}) }
}
