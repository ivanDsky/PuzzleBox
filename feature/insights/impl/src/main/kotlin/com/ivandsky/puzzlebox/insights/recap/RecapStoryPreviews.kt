package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

// The recap story on its second card over the deck, and held to peek at the third, light and dark. A preview never
// animates, so the deck is already dealt and at rest.

private val Zip = GameId("zip")

private val Cards = listOf(
    RecapCard.Solved(total = 42, perGame = listOf(RecapCard.Solved.PerGame(Zip, 42))),
    RecapCard.Streak(days = 17, from = LocalDate.of(2026, 11, 3), to = LocalDate.of(2026, 11, 19), freezesSpent = 2),
    RecapCard.Solved(total = 1, perGame = listOf(RecapCard.Solved.PerGame(Zip, 1))),
)

@PuzzleBoxPreviews
@Composable
private fun RecapStoryPreview() = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) { Story(RecapDeckState(Cards.size, initialPage = 1).apply { dealt() }) }
}

@PuzzleBoxPreviews
@Composable
private fun RecapStoryHeldPreview() = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        Story(RecapDeckState(Cards.size, initialPage = 1).apply { dealt(); hold(true) })
    }
}

@Composable
private fun Story(deck: RecapDeckState) =
    RecapStory(Cards, deck, "November 2026", gameName = { "Zip" }, onWatchReplay = {}, onClose = {})
