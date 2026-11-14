package com.ivandsky.puzzlebox.history.ranks

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ivandsky.puzzlebox.domain.ranks.OverallStandings
import com.ivandsky.puzzlebox.domain.ranks.OverallState
import com.ivandsky.puzzlebox.domain.ranks.RankScope
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.board.DailyBoardLine
import com.ivandsky.puzzlebox.gameui.board.DailyBoardRowModel
import com.ivandsky.puzzlebox.gameui.board.DailyBoardState
import com.ivandsky.puzzlebox.gameui.board.GhostRowModel
import com.ivandsky.puzzlebox.history.CalendarState
import com.ivandsky.puzzlebox.history.GOLDEN_PHONE
import com.ivandsky.puzzlebox.history.GOLDEN_TABLET
import com.ivandsky.puzzlebox.history.HistoryLayout
import com.ivandsky.puzzlebox.history.settleAndCapture
import com.ivandsky.puzzlebox.history.showGolden
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Ranks hub as the approved build shows it, light and dark: the phone on a Friends day board and on Overall, and the
 * tablet's two panes. Goldens in `snapshots/ranks/`, the visual baseline for any later change to the hub.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class RanksPhoneGoldenScreenshots : RanksGoldenScreenshots("phone") {
    @Test fun `friends day light`() = capture("friends-day", FriendsDay, dark = false)
    @Test fun `friends day dark`() = capture("friends-day", FriendsDay, dark = true)

    @Test fun `overall light`() = capture("overall", Overall, dark = false)
    @Test fun `overall dark`() = capture("overall", Overall, dark = true)
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class RanksTabletGoldenScreenshots : RanksGoldenScreenshots("tablet") {
    @Test fun `two pane light`() = capture("two-pane", TwoPane, dark = false, HistoryLayout.TwoPane)
    @Test fun `two pane dark`() = capture("two-pane", TwoPane, dark = true, HistoryLayout.TwoPane)
}

abstract class RanksGoldenScreenshots(private val device: String) {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    internal fun capture(name: String, state: RanksUiState, dark: Boolean, layout: HistoryLayout = HistoryLayout.SinglePane) {
        rule.showGolden(dark) {
            RanksScreen(state, RanksActions(onSignIn = {}, onSwipe = {}), layout = layout)
        }
        rule.settleAndCapture("ranks", name, device, dark)
    }

    internal companion object {
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

        private val Places = listOf(
            BoardPlace(RanksBoard.Overall, "Overall", Standing.Place(4)),
            BoardPlace(RanksBoard.Game(GameId("zip")), "Zip", Standing.Place(2)),
            BoardPlace(RanksBoard.Game(GameId("tango")), "Tango", Standing.Beyond),
            BoardPlace(RanksBoard.Game(GameId("queens")), "Queens", Standing.None),
            BoardPlace(RanksBoard.Game(GameId("strands")), "Strands", Standing.Pending),
        )

        private val Maya = DailyBoardRowModel(1, "Maya", 62_000L, 0, isSelf = false, playerId = "maya")
        private val You = DailyBoardRowModel(2, "You", 75_000L, 1, isSelf = true, playerId = "you")
        private val Jonas = DailyBoardRowModel(3, "Jonas", 131_000L, 3, isSelf = false, playerId = "jonas")

        private val ZipDay = RanksBoardSlice("Zip · Nov 12", RankOrder.TimeThenHints, DailyBoardState.Ranked(listOf(Maya, You, Jonas)))

        // Friends keeps a LinkedIn connection's time as a ghost line among the friends, which shifts the places under it.
        private val FriendsZipDay = RanksBoardSlice(
            "Zip · Nov 12",
            RankOrder.TimeThenHints,
            DailyBoardState.Ranked(
                rows = listOf(Maya, You, Jonas),
                lines = listOf(
                    DailyBoardLine.Real(Maya, place = 1),
                    DailyBoardLine.Ghost(GhostRowModel("priya", "Priya", timeMs = 70_000L, hintFree = true), place = 2),
                    DailyBoardLine.Real(You, place = 3),
                    DailyBoardLine.Real(Jonas, place = 4),
                ),
            ),
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

        private val OverallDay = RanksBoardSlice(
            "Overall · Nov 12",
            RankOrder.TimeThenHints,
            OverallState.Partial(Standings, loaded = 2, total = 5).toDailyBoardState("you"),
            "Points from every game's board. Higher is better.",
            "2 of 5 boards",
        )

        val FriendsDay = RanksUiState(
            FriendsZipDay,
            position = 1,
            boardCount = Places.size,
            calendar = Week,
            stats = Stats,
            yourSolve = PreviewYourSolve,
            scope = RankScope.Friends,
            places = Places,
        )

        val Overall = RanksUiState(OverallDay, boardCount = Places.size, calendar = Week, stats = Stats, places = Places)

        val TwoPane = RanksUiState(
            ZipDay,
            position = 1,
            boardCount = Places.size,
            calendar = Week.copy(expanded = true),
            stats = Stats,
            yourSolve = PreviewYourSolve,
            places = Places,
        )
    }
}
