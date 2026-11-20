package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.foundation.layout.Arrangement
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
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.insights.domain.recap.RecapPeriod
import java.time.LocalDate
import java.time.YearMonth

// The Longest streak page, its dial and chips and its tablet list fact, light and dark. A preview never animates, so
// the ring is fully lit.

private val November = RecapPeriod.Month(YearMonth.of(2026, 11))

private val Month = RecapCard.Streak(
    days = 17, from = LocalDate.of(2026, 11, 3), to = LocalDate.of(2026, 11, 19), freezesSpent = 2,
    frozenDays = setOf(LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 14)), span = November,
)

private val OneDay = RecapCard.Streak(
    days = 1, from = LocalDate.of(2026, 11, 9), to = LocalDate.of(2026, 11, 9), freezesSpent = 0,
    frozenDays = emptySet(), span = November,
)

private val Year = RecapCard.Streak(
    days = 74, from = LocalDate.of(2026, 6, 24), to = LocalDate.of(2026, 9, 5), freezesSpent = 3,
    frozenDays = setOf(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 15), LocalDate.of(2026, 8, 5)),
    span = RecapPeriod.Year(2026),
)

@PuzzleBoxPreviews
@Composable
private fun StreakCardPagePreview() = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) { StreakCardPage(Month, "November 2026") }
}

@PuzzleBoxPreviews
@Composable
private fun StreakCardPageOneDayPreview() = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) { StreakCardPage(OneDay, "November 2026") }
}

@PuzzleBoxPreviews
@Composable
private fun StreakCardPageYearPreview() = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) { StreakCardPage(Year, "2026 so far") }
}

@PuzzleBoxPreviews
@Composable
private fun StreakDialPreview() = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) { StreakDial(Month, Modifier.padding(Spacing.lg).size(320.dp)) }
}

@PuzzleBoxPreviews
@Composable
private fun StreakChipsPreview() = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            StreakChips(Month)
            StreakChips(OneDay)
        }
    }
}

@PuzzleBoxPreviews
@Composable
private fun StreakCardFactPreview() = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(Spacing.lg)) { StreakCardFact(Month) }
    }
}
