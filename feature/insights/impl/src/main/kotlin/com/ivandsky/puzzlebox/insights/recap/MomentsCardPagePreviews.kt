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

// The Moments earned page and its tablet list fact, light and dark: a month with every local kind, and a month with one. A preview never animates, so the count shows its end value.

private val Month = RecapCard.Moments(personalBests = 5, firstSubX = 2, fullSets = 14, streakMilestones = listOf(7, 30))

private val One = RecapCard.Moments(personalBests = 0, firstSubX = 0, fullSets = 1, streakMilestones = emptyList())

@PuzzleBoxPreviews
@Composable
private fun MomentsCardPagePreview() = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) { MomentsCardPage(Month, "November 2026") }
}

@PuzzleBoxPreviews
@Composable
private fun MomentsCardPageOnePreview() = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) { MomentsCardPage(One, "November 2026") }
}

@PuzzleBoxPreviews
@Composable
private fun MomentsCardFactPreview() = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(Spacing.lg)) { MomentsCardFact(Month) }
    }
}
