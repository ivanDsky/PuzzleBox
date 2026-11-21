package com.ivandsky.puzzlebox.play

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.community.CommunityActions
import com.ivandsky.puzzlebox.play.community.CommunityPhase
import com.ivandsky.puzzlebox.play.community.CommunityRowModel
import com.ivandsky.puzzlebox.play.community.CommunityState
import com.ivandsky.puzzlebox.play.community.GameChoice

// Games and Community on Today's large top bar: phone at its narrowest and the tablet, light and dark.

private val BarZip = GameBrand(AccentSlot.Blue, GameGlyphs.Zip)

private val BarActions = CommunityActions(onOpen = {}, onReport = {}, onGame = {}, onDifficulty = {}, onClearFilters = {}, onRetry = {})

private val BarCommunity = CommunityState(
    games = listOf(GameChoice(GameId("zip"), "Zip", BarZip)),
    phase = CommunityPhase.Loaded(
        listOf(
            CommunityRowModel(checkNotNull(ShareCode.parse("P4R8NT")), "Zip", BarZip, "Spiral staircase", author = "Maya", difficulty = Difficulty.Level.Hard, age = "5 min ago"),
        ),
    ),
)

@Composable
private fun Games(layout: PlayLayout) {
    PuzzleBoxTheme {
        Box(Modifier.background(MaterialTheme.colorScheme.background)) {
            LibraryScreen(
                state = previewState(),
                page = null,
                month = null,
                layout = layout,
                hasLesson = { true },
                onSelect = {},
                onPlay = { _, _ -> },
                onRetry = {},
                onOpenLesson = {},
                calendar = CalendarActions(),
                onOpenRanks = { _, _ -> },
                onEnterCode = {},
                onMakeOne = {},
            )
        }
    }
}

@Composable
private fun Community(layout: PlayLayout) {
    PuzzleBoxTheme {
        Surface(Modifier.fillMaxSize()) {
            if (layout == PlayLayout.SinglePane) {
                CommunityTabs(BarCommunity, BarActions, FriendPreviewCards, previewYoursSlot(3), onOpen = {}, onRemove = {}, onEnterCode = {})
            } else {
                CommunityPanes(
                    default = { null },
                    list = { size, picks ->
                        CommunityTabs(BarCommunity, BarActions, FriendPreviewCards, previewYoursSlot(3), {}, {}, {}, size, picks)
                    },
                    empty = { tab -> CommunityEmptyPane(tab, BarCommunity.phase) },
                    detail = { _, _ -> },
                )
            }
        }
    }
}

@Preview(name = "Phone Light", widthDp = 360, heightDp = 640, showBackground = true)
@Preview(name = "Phone Dark", widthDp = 360, heightDp = 640, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun GamesLargeBarPhonePreview() = Games(PlayLayout.SinglePane)

@Preview(name = "Tablet Light", widthDp = 1280, heightDp = 800, showBackground = true)
@Preview(name = "Tablet Dark", widthDp = 1280, heightDp = 800, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun GamesLargeBarTabletPreview() = Games(PlayLayout.TwoPane)

@Preview(name = "Phone Light", widthDp = 360, heightDp = 640, showBackground = true)
@Preview(name = "Phone Dark", widthDp = 360, heightDp = 640, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun CommunityLargeBarPhonePreview() = Community(PlayLayout.SinglePane)

@Preview(name = "Tablet Light", widthDp = 1280, heightDp = 800, showBackground = true)
@Preview(name = "Tablet Dark", widthDp = 1280, heightDp = 800, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
internal fun CommunityLargeBarTabletPreview() = Community(PlayLayout.TwoPane)
