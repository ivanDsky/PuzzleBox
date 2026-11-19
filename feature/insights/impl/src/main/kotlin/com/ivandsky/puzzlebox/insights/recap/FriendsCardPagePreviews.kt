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
import com.ivandsky.puzzlebox.insights.domain.recap.FriendsStanding
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

// The Best game among friends page and its tablet list fact, light and dark: a best game, results still settling, and
// the read that could not finish.

private val Zip = GameId("zip")
private val Queens = GameId("queens")

private val Names = mapOf(Zip to "Zip", Queens to "Queens")

private fun name(game: GameId): String = Names.getValue(game)

private val Loaded = RecapCard.Friends(FriendsStanding.Loaded(Queens, points = 1_340.0))
private val Settling = RecapCard.Friends(FriendsStanding.Settling(LocalDate.of(2026, 12, 4)))
private val Unavailable = RecapCard.Friends(FriendsStanding.Unavailable)

@PuzzleBoxPreviews
@Composable
private fun FriendsCardPageLoadedPreview() = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) { FriendsCardPage(Loaded, "November 2026", ::name) }
}

@PuzzleBoxPreviews
@Composable
private fun FriendsCardPageSettlingPreview() = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) { FriendsCardPage(Settling, "November 2026", ::name) }
}

@PuzzleBoxPreviews
@Composable
private fun FriendsCardPageUnavailablePreview() = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) { FriendsCardPage(Unavailable, "November 2026", ::name) }
}

@PuzzleBoxPreviews
@Composable
private fun FriendsCardFactPreview() = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(Modifier.padding(Spacing.lg)) {
            FriendsCardFact(Loaded, ::name)
            FriendsCardFact(Settling, ::name)
            FriendsCardFact(Unavailable, ::name)
        }
    }
}
