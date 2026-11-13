package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.runtime.Composable
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.board.DailyBoardRowModel
import com.ivandsky.puzzlebox.gameui.board.DailyBoardState

// One preview per state of the board slice.

private val Rows = listOf(
    DailyBoardRowModel(1, "Maya", 62_000L, 0, isSelf = false, playerId = "maya"),
    DailyBoardRowModel(2, "You", 75_000L, 1, isSelf = true, playerId = "you"),
    DailyBoardRowModel(3, "Jonas", 131_000L, 3, isSelf = false, playerId = "jonas"),
)

@Composable
private fun Hub(state: DailyBoardState) {
    PuzzleBoxTheme {
        RanksScreen(RanksUiState(RanksBoardSlice("Zip · today", RankOrder.TimeThenHints, state)))
    }
}

@PuzzleBoxPreviews
@Composable
private fun RanksBoardRankedPreview() = Hub(DailyBoardState.Ranked(Rows))

@PuzzleBoxPreviews
@Composable
private fun RanksBoardEmptyPreview() = Hub(DailyBoardState.Ranked(emptyList()))

@PuzzleBoxPreviews
@Composable
private fun RanksBoardLoadingPreview() = Hub(DailyBoardState.Loading)

@PuzzleBoxPreviews
@Composable
private fun RanksBoardUnavailablePreview() = Hub(DailyBoardState.Unavailable)

@PuzzleBoxPreviews
@Composable
private fun RanksBoardSignedOutPreview() = Hub(DailyBoardState.SignedOut)
