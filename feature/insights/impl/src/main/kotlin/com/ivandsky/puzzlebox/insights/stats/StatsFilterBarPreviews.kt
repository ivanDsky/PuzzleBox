package com.ivandsky.puzzlebox.insights.stats

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.insights.domain.stats.SectionState
import com.ivandsky.puzzlebox.insights.domain.stats.StatsRange
import com.ivandsky.puzzlebox.insights.domain.stats.StatsSection
import com.ivandsky.puzzlebox.insights.domain.stats.StatsSince
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate

// The stats page: the phone's filter bar and the tablet's "Overall" list with a quiet Since, light and dark, with no game picked and with one picked.

private val Zip = GameId("zip")
private val Games = listOf(Zip, GameId("queens"), GameId("tango"))

private fun name(game: GameId): String = game.value.replaceFirstChar(Char::uppercase)

private val Since = StatsSince(today = LocalDate.of(2026, 11, 19), firstSolve = LocalDate.of(2025, 3, 30))

private fun filtered(game: GameId?, range: StatsRange = StatsRange.Week) = StatsUiState(
    range = range,
    game = game,
    body = StatsBody.Ready(listOf(StatsSection.Records(SectionState.Unavailable))),
    since = Since,
)

@Composable
private fun Page(state: StatsUiState) = PuzzleBoxTheme {
    Surface(color = MaterialTheme.colorScheme.background) {
        StatsScreen(state, Games, ::name, onSelectRange = {}, onSelectGame = {}, onOpenSolve = {}, onBack = {})
    }
}

@PuzzleBoxPreviews
@Composable
private fun StatsFilterBarUnsetPreview() = Page(filtered(game = null))

@PuzzleBoxPreviews
@Composable
private fun StatsFilterBarSetPreview() = Page(filtered(game = Zip, range = StatsRange.Month))

@Preview(name = "Tablet Light", widthDp = 1280, heightDp = 800, showBackground = true)
@Preview(name = "Tablet Dark", widthDp = 1280, heightDp = 800, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StatsFilterTabletUnsetPreview() = Page(filtered(game = null))

@Preview(name = "Tablet Light", widthDp = 1280, heightDp = 800, showBackground = true)
@Preview(name = "Tablet Dark", widthDp = 1280, heightDp = 800, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StatsFilterTabletSetPreview() = Page(filtered(game = Zip, range = StatsRange.All))
