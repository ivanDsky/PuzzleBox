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
import com.ivandsky.puzzlebox.insights.domain.recap.DayTally
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.insights.domain.recap.RecapPeriod
import java.time.LocalDate
import java.time.YearMonth

// The Total time page and its tablet list fact, light and dark: a month, and a year. A preview never animates, so the river is settled.

private val Month = RecapCard.TotalTime(
    totalMs = 11_535_000,
    solved = 42,
    days = (1..28).map { DayTally(LocalDate.of(2026, 11, it), solved = it % 4 + 1, timeMs = (it % 7 + 1) * 400_000L, hintFree = 1) },
    period = RecapPeriod.Month(YearMonth.of(2026, 11)),
)

private val Year = RecapCard.TotalTime(
    totalMs = 512_461_000,
    solved = 981,
    days = (0 until 40).map { DayTally(LocalDate.of(2026, 1, 1).plusDays(it * 7L), solved = 20, timeMs = (it % 9 + 3) * 3_000_000L, hintFree = 10) },
    period = RecapPeriod.Year(2026),
)

@PuzzleBoxPreviews
@Composable
private fun TotalTimeCardPagePreview() = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) { TotalTimeCardPage(Month, "November 2026") }
}

@PuzzleBoxPreviews
@Composable
private fun TotalTimeCardPageYearPreview() = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) { TotalTimeCardPage(Year, "2026 so far") }
}

@PuzzleBoxPreviews
@Composable
private fun TotalTimeCardFactPreview() = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(Spacing.lg)) { TotalTimeCardFact(Month) }
    }
}
