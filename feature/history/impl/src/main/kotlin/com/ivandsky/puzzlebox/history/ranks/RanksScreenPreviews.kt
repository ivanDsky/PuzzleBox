package com.ivandsky.puzzlebox.history.ranks

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxPreviews
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.domain.ranks.OverallStandings
import com.ivandsky.puzzlebox.domain.ranks.OverallState
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.gameui.board.DailyBoardRowModel
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.board.DailyBoardState
import com.ivandsky.puzzlebox.history.CalendarState
import com.ivandsky.puzzlebox.history.HistoryDayRow
import com.ivandsky.puzzlebox.history.HistoryLayout
import com.ivandsky.puzzlebox.history.HistoryReplays
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.LocalDate

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
private fun Hub(slice: RanksBoardSlice, signIn: RanksSignIn = RanksSignIn.Idle) = Hub(RanksUiState(slice, signIn, position = 1, boardCount = 6))

@Composable
private fun Hub(state: RanksUiState) {
    RanksPreviewHost {
        RanksScreen(state, RanksActions(onSignIn = {}, onSwipe = {}))
    }
}

private val Today = LocalDate.of(2026, 11, 13)
private val Yesterday = Today.minusDays(1)

private val Week = CalendarState(
    shownDay = Yesterday,
    earliestDay = Today.minusDays(40),
    selectedDay = Yesterday,
    solvedByDay = mapOf(Yesterday to listOf(GameBrand.Neutral), Today to listOf(GameBrand.Neutral)),
    daysWithRows = setOf(Yesterday, Today),
    gamesPerDay = 3,
    today = Today,
)

private val Stats = GameStats(4, 9, 31, 22, 95_000L, Today.minusDays(40), best = null, rankOrder = RankOrder.TimeThenHints)

internal val PreviewYourSolve = YourSolve(
    row = HistoryDayRow(
        day = Yesterday,
        puzzleNumber = 594,
        gameId = GameId("zip"),
        gameName = "Zip",
        record = CompletionRecord(
            puzzleRef = object : PuzzleRef {
                override val gameId = GameId("zip")
                override val key = PuzzleKey.Daily(Yesterday, 594)
            },
            time = 95_000L,
            hints = 1,
            stats = RecordStats(1, "{}"),
        ),
    ),
    replays = HistoryReplays.Hidden,
    playUi = null,
)

private val ZipSlice = RanksBoardSlice("Zip · Nov 12", RankOrder.TimeThenHints, DailyBoardState.Ranked(Rows))

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

@PuzzleBoxPreviews
@Composable
private fun RanksWeekStripPreview() = Hub(RanksUiState(ZipSlice, calendar = Week, stats = Stats))

@PuzzleBoxPreviews
@Composable
private fun RanksMonthWithStatsPreview() = Hub(RanksUiState(ZipSlice, calendar = Week.copy(expanded = true), stats = Stats))

@PuzzleBoxPreviews
@Composable
private fun RanksYourSolvePreview() = Hub(RanksUiState(ZipSlice, calendar = Week, stats = Stats, yourSolve = PreviewYourSolve))

private val TabletPlaces = listOf(
    BoardPlace(RanksBoard.Overall, "Overall", Standing.Place(4)),
    BoardPlace(RanksBoard.Game(GameId("zip")), "Zip", Standing.Place(2)),
    BoardPlace(RanksBoard.Game(GameId("tango")), "Tango", Standing.Beyond),
    BoardPlace(RanksBoard.Game(GameId("queens")), "Queens", Standing.None),
    BoardPlace(RanksBoard.Game(GameId("strands")), "Strands", Standing.Pending),
)

@Composable
private fun Tablet(state: RanksUiState) {
    RanksPreviewHost {
        RanksScreen(state, RanksActions(onSignIn = {}, onSwipe = {}), layout = HistoryLayout.TwoPane)
    }
}

// A Galaxy Tab S9 Ultra held landscape: 14.6", 2960 x 1848 px at 2x.
private const val TabWidth = 1480
private const val TabHeight = 924

@Preview(name = "Tab S9 Ultra Light", widthDp = TabWidth, heightDp = TabHeight, showBackground = true)
@Preview(name = "Tab S9 Ultra Dark", widthDp = TabWidth, heightDp = TabHeight, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RanksTwoPanePreview() = Tablet(
    RanksUiState(ZipSlice, position = 1, boardCount = TabletPlaces.size, calendar = Week.copy(expanded = true), stats = Stats, places = TabletPlaces),
)

@Preview(name = "Tab S9 Ultra Light", widthDp = TabWidth, heightDp = TabHeight, showBackground = true)
@Preview(name = "Tab S9 Ultra Dark", widthDp = TabWidth, heightDp = TabHeight, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RanksTwoPaneOverallPreview() = Tablet(
    RanksUiState(
        RanksBoardSlice("Overall · today", RankOrder.TimeThenHints, OverallState.Partial(Standings, loaded = 2, total = 5).toDailyBoardState("you"), "Points from every game's board. Higher is better.", "2 of 5 boards"),
        boardCount = TabletPlaces.size,
        calendar = Week.copy(expanded = true),
        stats = Stats,
        places = TabletPlaces,
    ),
)

@Preview(name = "Tab S9 Ultra Light", widthDp = TabWidth, heightDp = TabHeight, showBackground = true)
@Preview(name = "Tab S9 Ultra Dark", widthDp = TabWidth, heightDp = TabHeight, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun RanksTwoPanePastDayPreview() = Tablet(
    RanksUiState(ZipSlice, position = 1, boardCount = TabletPlaces.size, calendar = Week.copy(expanded = true), stats = Stats, yourSolve = PreviewYourSolve, places = TabletPlaces),
)
