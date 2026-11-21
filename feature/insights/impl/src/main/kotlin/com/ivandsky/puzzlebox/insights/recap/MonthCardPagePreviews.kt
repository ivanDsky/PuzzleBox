package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.insights.domain.recap.EarnedMoment
import com.ivandsky.puzzlebox.insights.domain.recap.EarnedMoment.Kind
import com.ivandsky.puzzlebox.insights.domain.recap.MonthHighlight
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import java.time.YearMonth

// The month-of-the-year page and its tablet list fact, light and dark: a month with four stamps, one stamp to eight
// (five and "+3 more"), a month with none, the longest words and numbers, and the fact block. A preview never
// animates, so the count shows its end value and every sticker is down.

private val Zip = GameId("zip")
private val Queens = GameId("queens")
private val Mini = GameId("mini-sudoku")

private val Names = mapOf(Zip to "Zip", Queens to "Queens", Mini to "Mini Sudoku")

private fun name(game: GameId): String = Names.getValue(game)

private fun jan(day: Int) = LocalDate.of(2026, 1, day)

/** Eight stamps across January; a card shows the first [count]. */
private val JanuaryStamps = listOf(
    EarnedMoment(Kind.Streak, jan(7), streakDays = 7),
    EarnedMoment(Kind.PersonalBest, jan(9), Zip),
    EarnedMoment(Kind.PersonalBest, jan(14), Queens),
    EarnedMoment(Kind.FirstSubX, jan(19), Zip, thresholdMs = 10_000),
    EarnedMoment(Kind.FullSet, jan(24)),
    EarnedMoment(Kind.PersonalBest, jan(27), Mini),
    EarnedMoment(Kind.FullSet, jan(29)),
    EarnedMoment(Kind.Streak, jan(30), streakDays = 30),
)

private fun january(count: Int) = RecapCard.Month(
    YearMonth.of(2026, 1), solved = 96, bestGame = Zip, bestTimeMs = 58_000, underAveragePercent = 31,
    fastestGame = Zip, fastestTimeMs = 11_000,
    lead = MonthHighlight.PersonalBest(Queens, jan(14), timeMs = 74_000, fastestAllYear = true),
    also = MonthHighlight.Streak(jan(7), days = 7),
    moments = JanuaryStamps.take(count),
)

private val Quiet = RecapCard.Month(
    YearMonth.of(2026, 10), solved = 1, bestGame = Zip, bestTimeMs = 141_000, underAveragePercent = 0,
    fastestGame = Zip, fastestTimeMs = 141_000, lead = MonthHighlight.QuietMonth(solved = 1, mostlyGame = Zip), also = null, moments = emptyList(),
)

private val Longest = RecapCard.Month(
    YearMonth.of(2026, 9), solved = 124, bestGame = Mini, bestTimeMs = 3_723_000, underAveragePercent = 12,
    fastestGame = Mini, fastestTimeMs = 3_723_000,
    lead = MonthHighlight.PersonalBest(Mini, LocalDate.of(2026, 9, 21), timeMs = 3_723_000, fastestAllYear = false),
    also = MonthHighlight.FirstSubX(Zip, LocalDate.of(2026, 9, 3), thresholdMs = 10_000),
    moments = listOf(
        EarnedMoment(Kind.FirstSubX, LocalDate.of(2026, 9, 3), Zip, thresholdMs = 10_000),
        EarnedMoment(Kind.FullSet, LocalDate.of(2026, 9, 12)),
        EarnedMoment(Kind.PersonalBest, LocalDate.of(2026, 9, 21), Mini),
        EarnedMoment(Kind.Streak, LocalDate.of(2026, 9, 30), streakDays = 100),
    ),
)

@Composable
private fun Phone(card: RecapCard.Month, place: MonthPlace) = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        Box(Modifier.size(412.dp, 760.dp)) { MonthCardPage(card, "2026 so far", ::name, place = place) }
    }
}

@PuzzleBoxPreviews
@Composable
private fun MonthCardPagePreview() = Phone(january(4), MonthPlace(1, 12, "February"))

@PuzzleBoxPreviews
@Composable
private fun MonthCardPageOneStampPreview() = Phone(january(1), MonthPlace(1, 12, "February"))

@PuzzleBoxPreviews
@Composable
private fun MonthCardPageTwoStampsPreview() = Phone(january(2), MonthPlace(1, 12, "February"))

@PuzzleBoxPreviews
@Composable
private fun MonthCardPageThreeStampsPreview() = Phone(january(3), MonthPlace(1, 12, "February"))

@PuzzleBoxPreviews
@Composable
private fun MonthCardPageFiveStampsPreview() = Phone(january(5), MonthPlace(1, 12, "February"))

@PuzzleBoxPreviews
@Composable
private fun MonthCardPageSixStampsPreview() = Phone(january(6), MonthPlace(1, 12, "February"))

/** Eight stamps: five on the board and "+3 more". */
@PuzzleBoxPreviews
@Composable
private fun MonthCardPageOverflowPreview() = Phone(january(8), MonthPlace(1, 12, "February"))

@PuzzleBoxPreviews
@Composable
private fun MonthCardPageEmptyPreview() = Phone(Quiet, MonthPlace(1, 2, "November"))

@PuzzleBoxPreviews
@Composable
private fun MonthCardPageLongestPreview() = Phone(Longest, MonthPlace(9, 12, "October"))

@PuzzleBoxPreviews
@Composable
private fun MonthCardFactPreview() = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(Spacing.lg)) { MonthCardFact(january(4), ::name) }
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
