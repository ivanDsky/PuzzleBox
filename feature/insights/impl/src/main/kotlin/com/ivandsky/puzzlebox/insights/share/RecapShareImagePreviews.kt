package com.ivandsky.puzzlebox.insights.share

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.insights.domain.recap.RecapPeriod
import java.time.LocalDate
import java.time.YearMonth

// The share image for a month and for a year still under way (A22), light and dark, drawn at a quarter of its real size.

private val Cards = listOf(
    RecapCard.Solved(total = 42, perGame = emptyList()),
    RecapCard.TotalTime(totalMs = 11_535_000, solved = 42, days = emptyList(), period = RecapPeriod.Month(YearMonth.of(2026, 11))),
    RecapCard.Streak(days = 17, from = LocalDate.of(2026, 11, 3), to = LocalDate.of(2026, 11, 19), freezesSpent = 2),
    RecapCard.HintFree(hintFree = 36, solved = 42),
    RecapCard.Moments(personalBests = 5, firstSubX = 2, fullSets = 14, streakMilestones = listOf(7, 30)),
)

@PuzzleBoxPreviews
@Composable
private fun RecapShareImageMonthPreview() = PuzzleBoxTheme {
    ScaledToFit(Modifier.width(270.dp).height(480.dp)) { RecapShareImage(RecapShareContent.of("November 2026", "Ada", Cards)) }
}

@PuzzleBoxPreviews
@Composable
private fun RecapShareImageYearSoFarPreview() = PuzzleBoxTheme {
    ScaledToFit(Modifier.width(270.dp).height(480.dp)) { RecapShareImage(RecapShareContent.of("2026 so far", null, Cards)) }
}
