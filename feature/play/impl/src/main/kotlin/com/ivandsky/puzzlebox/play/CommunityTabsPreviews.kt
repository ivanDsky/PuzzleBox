package com.ivandsky.puzzlebox.play

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.EmptyState
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.PrimaryButton
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.community.CommunityActions
import com.ivandsky.puzzlebox.play.community.CommunityPhase
import com.ivandsky.puzzlebox.play.community.CommunityRowModel
import com.ivandsky.puzzlebox.play.community.CommunityState
import com.ivandsky.puzzlebox.play.community.DifficultyChoice
import com.ivandsky.puzzlebox.play.community.GameChoice

private val Zip = GameBrand(AccentSlot.Blue, GameGlyphs.Zip)
private val Tango = GameBrand(AccentSlot.Orange, GameGlyphs.Tango)

private val Actions = CommunityActions(onOpen = {}, onReport = {}, onGame = {}, onDifficulty = {}, onClearFilters = {}, onRetry = {})

private fun row(code: String, game: String, brand: GameBrand, title: String, level: Difficulty.Level?, age: String) =
    CommunityRowModel(checkNotNull(ShareCode.parse(code)), game, brand, title, author = "Maya", difficulty = level, age = age)

private val Loaded = CommunityState(
    games = listOf(GameChoice(GameId("zip"), "Zip", Zip), GameChoice(GameId("tango"), "Tango", Tango)),
    phase = CommunityPhase.Loaded(
        listOf(
            row("P4R8NT", "Zip", Zip, "Spiral staircase", Difficulty.Level.Hard, "5 min ago"),
            row("W9C3HB", "Tango", Tango, "Sun and moon", null, "2 h ago"),
        ),
    ),
)

/**
 * A stand-in for the app's slot, whose Yours list is create's: New puzzle over [count] drafts, or with none the
 * first-time message, with [published] as the newest published code. Previews and the tabs' goldens share it.
 */
internal fun previewYoursSlot(count: Int, published: String? = null) = CustomSlot(
    yours = { listState ->
        if (count == 0) {
            EmptyState("Make a puzzle for someone", "Pick a game, build the board, and share a short code.")
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                item { PrimaryButton("New puzzle", onClick = {}, modifier = Modifier.fillMaxWidth()) }
                item { FriendsSectionHead("Continue", count) }
                items(count) { Text("Draft ${it + 1}", style = MaterialTheme.typography.titleMedium) }
            }
        }
    },
    yoursSummary = { YoursSummary(count, published) },
)

@Composable
private fun Tabs(state: CommunityState, friends: List<FriendCardModel>, yours: Int) {
    PuzzleBoxTheme {
        Surface(Modifier.fillMaxSize()) {
            CommunityTabs(state, Actions, friends, previewYoursSlot(yours), onOpen = {}, onRemove = {}, onEnterCode = {})
        }
    }
}

@PuzzleBoxPreviews
@Composable
internal fun CommunityTabsPreview() = Tabs(Loaded, FriendPreviewCards, yours = 3)

@PuzzleBoxPreviews
@Composable
internal fun CommunityTabsFilteredPreview() = Tabs(Loaded.copy(game = GameId("zip")), FriendPreviewCards, yours = 3)

@PuzzleBoxPreviews
@Composable
internal fun CommunityTabsChipsPreview() = Tabs(Loaded.copy(filterSheets = true), FriendPreviewCards, yours = 3)

@PuzzleBoxPreviews
@Composable
internal fun CommunityTabsChipsFilteredPreview() =
    Tabs(Loaded.copy(filterSheets = true, game = GameId("zip"), difficulty = DifficultyChoice.Hard), FriendPreviewCards, yours = 3)

@PuzzleBoxPreviews
@Composable
internal fun CommunityTabsEmptyPreview() = Tabs(Loaded.copy(phase = CommunityPhase.Loaded(emptyList())), emptyList(), yours = 0)

@PuzzleBoxPreviews
@Composable
internal fun CommunityFriendsPageOfflinePreview() {
    PuzzleBoxTheme {
        Surface {
            CommunityFriendsPage(FriendPreviewCards, offline = true, rememberLazyListState(), onOpen = {}, onRemove = {})
        }
    }
}
