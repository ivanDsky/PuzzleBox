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
import java.time.LocalDate

// The Longest streak page and its tablet list fact, light and dark. A preview never animates, so the count shows its end value.

private val Month = RecapCard.Streak(days = 17, from = LocalDate.of(2026, 11, 3), to = LocalDate.of(2026, 11, 19), freezesSpent = 2)

private val OneDay = RecapCard.Streak(days = 1, from = LocalDate.of(2026, 11, 9), to = LocalDate.of(2026, 11, 9), freezesSpent = 0)

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
private fun StreakCardFactPreview() = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(Spacing.lg)) { StreakCardFact(Month) }
    }
}
