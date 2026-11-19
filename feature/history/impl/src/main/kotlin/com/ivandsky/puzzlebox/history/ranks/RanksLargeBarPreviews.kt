package com.ivandsky.puzzlebox.history.ranks

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.history.HistoryLayout
import com.ivandsky.puzzlebox.history.domain.ranks.RankScope
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.resultui.board.DailyBoardRowModel
import com.ivandsky.puzzlebox.resultui.board.DailyBoardState

// The tab's hub on Today's large top bar (MainTabsLargeBar on): phone at its narrowest and the tablet, light and dark.

private val BarRows = listOf(
    DailyBoardRowModel(1, "Maya", 62_000L, 0, isSelf = false, playerId = "maya"),
    DailyBoardRowModel(2, "You", 75_000L, 1, isSelf = true, playerId = "you"),
)

private val BarPlaces = listOf(
    BoardPlace(RanksBoard.Overall, "Overall", Standing.Place(4)),
    BoardPlace(RanksBoard.Game(GameId("zip")), "Zip", Standing.Place(2)),
    BoardPlace(RanksBoard.Game(GameId("minisudoku")), "Mini Sudoku", Standing.Place(7)),
)

private fun barState(position: Int, title: String, scope: RankScope = RankScope.Global, window: RanksSelection.Window = RanksSelection.Window.Day) =
    RanksUiState(
        RanksBoardSlice(title, RankOrder.TimeThenHints, DailyBoardState.Ranked(BarRows)),
        position = position,
        boardCount = BarPlaces.size,
        scope = scope,
        window = window,
        places = BarPlaces,
        largeBar = true,
    )

@Composable
private fun Hub(state: RanksUiState, layout: HistoryLayout = HistoryLayout.SinglePane) {
    RanksPreviewHost(Modifier.fillMaxSize()) {
        RanksScreen(state, RanksActions(onSignIn = {}, onSwipe = {}), layout = layout, tuneDefaults = previewTuneDefaults())
    }
}

@Preview(name = "Phone Light", widthDp = 360, heightDp = 640, showBackground = true)
@Preview(name = "Phone Dark", widthDp = 360, heightDp = 640, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RanksLargeBarPhonePreview() = Hub(barState(position = 1, title = "Zip · today"))

// The longest game name with a tuned scope and window: the title shrinks before its chevron is cut.
@Preview(name = "Phone long name Light", widthDp = 360, heightDp = 640, showBackground = true)
@Preview(name = "Phone long name Dark", widthDp = 360, heightDp = 640, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RanksLargeBarLongNamePreview() =
    Hub(barState(position = 2, title = "Mini Sudoku · this week", scope = RankScope.Friends, window = RanksSelection.Window.Week))

@Preview(name = "Tablet Light", widthDp = 1280, heightDp = 800, showBackground = true)
@Preview(name = "Tablet Dark", widthDp = 1280, heightDp = 800, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RanksLargeBarTabletPreview() = Hub(barState(position = 2, title = "Mini Sudoku"), HistoryLayout.TwoPane)
