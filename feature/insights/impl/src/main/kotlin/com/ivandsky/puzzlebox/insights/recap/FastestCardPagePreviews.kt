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
import java.time.LocalDate

// The Fastest solve page and its tablet list fact, light and dark: games with a replay, and an imported time without one.

private val Zip = GameId("zip")
private val Queens = GameId("queens")
private val Sudoku = GameId("sudoku")

private val Names = mapOf(Zip to "Zip", Queens to "Queens", Sudoku to "Sudoku")

private fun name(game: GameId): String = Names.getValue(game)

private val Month = RecapCard.Fastest(
    rows = listOf(
        RecapCard.Fastest.Row(Zip, timeMs = 58_000, day = LocalDate.of(2026, 11, 3), puzzleNumber = 212, hasReplay = true),
        RecapCard.Fastest.Row(Queens, timeMs = 141_000, day = LocalDate.of(2026, 11, 12), puzzleNumber = 190, hasReplay = true),
        RecapCard.Fastest.Row(Sudoku, timeMs = 312_000, day = LocalDate.of(2026, 11, 8), puzzleNumber = null, hasReplay = false),
    ),
)

private val OneGame = RecapCard.Fastest(listOf(Month.rows.first()))

@PuzzleBoxPreviews
@Composable
private fun FastestCardPagePreview() = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) { FastestCardPage(Month, "November 2026", ::name, onWatchReplay = {}) }
}

@PuzzleBoxPreviews
@Composable
private fun FastestCardPageOneGamePreview() = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) { FastestCardPage(OneGame, "November 2026", ::name, onWatchReplay = {}) }
}

@PuzzleBoxPreviews
@Composable
private fun FastestCardFactPreview() = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(Spacing.lg)) { FastestCardFact(Month, ::name, onWatchReplay = {}) }
    }
}
