package com.ivandsky.puzzlebox.play.community

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode

private val Zip = GameBrand(AccentSlot.Blue, GameGlyphs.Zip)
private val Tango = GameBrand(AccentSlot.Orange, GameGlyphs.Tango)

private val Games = listOf(GameChoice(GameId("zip"), "Zip", Zip), GameChoice(GameId("tango"), "Tango", Tango))

private val Actions = CommunityActions(onOpen = {}, onReport = {}, onGame = {}, onDifficulty = {}, onClearFilters = {}, onRetry = {})

private fun row(code: String, game: String, brand: GameBrand, title: String, level: Difficulty.Level?, age: String) =
    CommunityRowModel(checkNotNull(ShareCode.parse(code)), game, brand, title, author = "Maya", difficulty = level, age = age)

@Composable
private fun Section(phase: CommunityPhase, filterSheets: Boolean = false, game: GameId? = null) {
    PuzzleBoxTheme {
        Surface {
            LazyColumn { communitySection(CommunityState(games = Games, game = game, phase = phase, filterSheets = filterSheets), Actions) }
        }
    }
}

@PuzzleBoxPreviews
@Composable
internal fun CommunitySectionPreview() = Section(
    CommunityPhase.Loaded(
        listOf(
            row("K7Q2MX", "Zip", Zip, "Spiral staircase", Difficulty.Level.Hard, "5 min ago"),
            row("P4R8NT", "Tango", Tango, "Sun and moon", null, "2 h ago"),
        ),
    ),
)

@PuzzleBoxPreviews
@Composable
internal fun CommunitySectionEmptyPreview() = Section(CommunityPhase.Loaded(emptyList()))

@PuzzleBoxPreviews
@Composable
internal fun CommunitySectionOfflinePreview() = Section(CommunityPhase.Offline)

@PuzzleBoxPreviews
@Composable
internal fun CommunitySectionChipsPreview() = Section(
    CommunityPhase.Loaded(
        listOf(
            row("K7Q2MX", "Zip", Zip, "Spiral staircase", Difficulty.Level.Hard, "5 min ago"),
            row("P4R8NT", "Tango", Tango, "Sun and moon", null, "2 h ago"),
        ),
    ),
    filterSheets = true,
)

@PuzzleBoxPreviews
@Composable
internal fun CommunitySectionFilteredEmptyPreview() = Section(CommunityPhase.Loaded(emptyList()), filterSheets = true, game = GameId("zip"))
