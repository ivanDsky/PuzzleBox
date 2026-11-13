package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.runtime.Composable
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.ranks.OverallStandings
import com.ivandsky.puzzlebox.domain.ranks.OverallState
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.board.DailyBoardRowModel
import com.ivandsky.puzzlebox.gameui.board.DailyBoardState

// One preview per state of the board slice.

private val Rows = listOf(
    DailyBoardRowModel(1, "Maya", 62_000L, 0, isSelf = false, playerId = "maya"),
    DailyBoardRowModel(2, "You", 75_000L, 1, isSelf = true, playerId = "you"),
    DailyBoardRowModel(3, "Jonas", 131_000L, 3, isSelf = false, playerId = "jonas"),
)

private val Standings = OverallStandings(
    listOf(
        OverallStandings.Row("maya", "Maya", 1, 291.5, 3, truncated = false),
        OverallStandings.Row("jonas", "Jonas", 2, 262.0, 3, truncated = false),
        OverallStandings.Row("lena", "Lena", 3, 240.0, 2, truncated = false),
        OverallStandings.Row("you", "You", 4, 190.4, 3, truncated = true),
        OverallStandings.Row("sam", "Sam", 5, 100.0, 1, truncated = false),
    ),
)

@Composable
private fun Hub(slice: RanksBoardSlice, signIn: RanksSignIn = RanksSignIn.Idle) {
    PuzzleBoxTheme {
        RanksScreen(RanksUiState(slice, signIn), onSignIn = {})
    }
}

@Composable
private fun Hub(state: DailyBoardState) = Hub(RanksBoardSlice("Zip · today", RankOrder.TimeThenHints, state))

@Composable
private fun Overall(state: OverallState) = Hub(
    RanksBoardSlice("Overall · today", RankOrder.TimeThenHints, state.toDailyBoardState("you"), "Points from every game's board. Higher is better.", state.boardNote()),
)

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

@PuzzleBoxPreviews
@Composable
private fun RanksBoardSigningInPreview() =
    Hub(RanksBoardSlice("Overall · today", RankOrder.TimeThenHints, DailyBoardState.SignedOut), RanksSignIn.SigningIn)

@PuzzleBoxPreviews
@Composable
private fun RanksBoardSignInFailedPreview() =
    Hub(RanksBoardSlice("Overall · today", RankOrder.TimeThenHints, DailyBoardState.SignedOut), RanksSignIn.Failed)

@PuzzleBoxPreviews
@Composable
private fun RanksOverallCompletePreview() = Overall(OverallState.Complete(Standings))

@PuzzleBoxPreviews
@Composable
private fun RanksOverallPartialPreview() = Overall(OverallState.Partial(Standings, loaded = 2, total = 5))

@PuzzleBoxPreviews
@Composable
private fun RanksOverallOfflinePreview() = Overall(OverallState.Offline(Standings))

@PuzzleBoxPreviews
@Composable
private fun RanksOverallEmptyPreview() = Overall(OverallState.Empty)

@PuzzleBoxPreviews
@Composable
private fun RanksOverallLoadingPreview() = Overall(OverallState.Loading)

@PuzzleBoxPreviews
@Composable
private fun RanksOverallUnavailablePreview() = Overall(OverallState.Offline(null))
