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

// The Hint-free solves page and its tablet list fact, light and dark: a month, and a single hint-free solve. A preview never animates, so the count shows its end value.

private val Month = RecapCard.HintFree(hintFree = 36, solved = 42)

private val One = RecapCard.HintFree(hintFree = 1, solved = 1)

@PuzzleBoxPreviews
@Composable
private fun HintFreeCardPagePreview() = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) { HintFreeCardPage(Month, "November 2026") }
}

@PuzzleBoxPreviews
@Composable
private fun HintFreeCardPageOnePreview() = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) { HintFreeCardPage(One, "November 2026") }
}

@PuzzleBoxPreviews
@Composable
private fun HintFreeCardFactPreview() = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(Spacing.lg)) { HintFreeCardFact(Month) }
    }
}
