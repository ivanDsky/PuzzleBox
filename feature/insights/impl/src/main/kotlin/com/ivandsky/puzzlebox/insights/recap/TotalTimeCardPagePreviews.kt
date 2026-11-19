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

// The Total time page and its tablet list fact, light and dark: a month, and a year past the hour mark. A preview never animates, so the count shows its end value.

private val Month = RecapCard.TotalTime(totalMs = 11_535_000, solved = 42)

private val Year = RecapCard.TotalTime(totalMs = 512_461_000, solved = 981)

@PuzzleBoxPreviews
@Composable
private fun TotalTimeCardPagePreview() = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) { TotalTimeCardPage(Month, "November 2026") }
}

@PuzzleBoxPreviews
@Composable
private fun TotalTimeCardPageYearPreview() = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) { TotalTimeCardPage(Year, "November 2026") }
}

@PuzzleBoxPreviews
@Composable
private fun TotalTimeCardFactPreview() = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(Spacing.lg)) { TotalTimeCardFact(Month) }
    }
}
