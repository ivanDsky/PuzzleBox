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

// The Puzzles solved page and its tablet list fact, light and dark. A preview never animates, so the count shows its end value.

private val Zip = GameId("zip")
private val Queens = GameId("queens")
private val Sudoku = GameId("sudoku")

private val Names = mapOf(Zip to "Zip", Queens to "Queens", Sudoku to "Sudoku")

private fun name(game: GameId): String = Names.getValue(game)

private val Month = RecapCard.Solved(
    total = 42,
    perGame = listOf(
        RecapCard.Solved.PerGame(Zip, 18),
        RecapCard.Solved.PerGame(Queens, 15),
        RecapCard.Solved.PerGame(Sudoku, 9),
    ),
)

private val OneSolve = RecapCard.Solved(total = 1, perGame = listOf(RecapCard.Solved.PerGame(Zip, 1)))

@PuzzleBoxPreviews
@Composable
private fun SolvedCardPagePreview() = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) { SolvedCardPage(Month, "November 2026", ::name) }
}

@PuzzleBoxPreviews
@Composable
private fun SolvedCardPageOneSolvePreview() = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) { SolvedCardPage(OneSolve, "November 2026", ::name) }
}

@PuzzleBoxPreviews
@Composable
private fun SolvedCardFactPreview() = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(Spacing.lg)) { SolvedCardFact(Month, ::name) }
    }
}
