package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.board.DailyBoardRowModel
import com.ivandsky.puzzlebox.gameui.board.DailyBoardState
import com.ivandsky.puzzlebox.history.CalendarState
import com.ivandsky.puzzlebox.history.HistoryLayout
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The tablet hub (spec Solution, tablet): the month and the game list on the left, the hero card above the board on the right. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
class RanksTwoPaneTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.of(2026, 11, 13)
    private val zip = RanksBoard.Game(GameId("zip"))
    private val places = listOf(
        BoardPlace(RanksBoard.Overall, "Overall", Standing.Place(4)),
        BoardPlace(zip, "Zip", Standing.Place(2)),
        BoardPlace(RanksBoard.Game(GameId("tango")), "Tango", Standing.None),
    )
    private val rows = listOf(
        DailyBoardRowModel(1, "Maya", 62_000L, 0, isSelf = false, playerId = "maya"),
        DailyBoardRowModel(2, "You", 75_000L, 1, isSelf = true, playerId = "you"),
    )
    private val calendar = CalendarState(
        shownDay = today,
        earliestDay = today.minusDays(30),
        daysWithRows = setOf(today),
        gamesPerDay = 1,
        today = today,
    )

    private fun show(
        position: Int = 1,
        onPickBoard: (RanksBoard) -> Unit = {},
        onShowCalendar: (LocalDate, Boolean) -> Unit = { _, _ -> },
    ) {
        val state = RanksUiState(
            RanksBoardSlice("Zip · today", RankOrder.TimeThenHints, DailyBoardState.Ranked(rows)),
            position = position,
            boardCount = places.size,
            calendar = calendar,
            places = places,
        )
        composeRule.setContent {
            PuzzleBoxTheme {
                RanksScreen(
                    state,
                    onSignIn = {},
                    onSwipe = {},
                    layout = HistoryLayout.TwoPane,
                    onPickBoard = onPickBoard,
                    onShowCalendar = onShowCalendar,
                )
            }
        }
    }

    @Test
    fun `two panes show the month, the game list and the board, with no title menu or dots`() {
        show()

        composeRule.onNodeWithTag("ranks.leftpane").assertIsDisplayed()
        composeRule.onNodeWithTag("ranks.rightpane").assertIsDisplayed()
        composeRule.onNodeWithTag("history.calendar.month", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("ranks.list.item.0").assertIsDisplayed()
        composeRule.onNodeWithTag("ranks.list.item.2").assertIsDisplayed()
        composeRule.onNodeWithTag("ranks.board").assertIsDisplayed()
        composeRule.onNodeWithTag("ranks.tune").assertIsDisplayed()
        composeRule.onAllNodesWithTag("ranks.title").assertCountEquals(0)
        composeRule.onAllNodesWithTag("ranks.dots").assertCountEquals(0)
    }

    @Test
    fun `the calendar is the month even when the hub's state holds a week`() {
        show()

        composeRule.onNodeWithTag("history.calendar.month", useUnmergedTree = true).assertTextEquals("November 2026")
    }

    @Test
    fun `the month has no fold chevron and no fold tap on the tablet`() {
        show()

        composeRule.onAllNodesWithTag("history.calendar.expand", useUnmergedTree = true).assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("Show one week", useUnmergedTree = true).assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("Show the month", useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun `the hero card gives the viewer's place on the shown board and their score`() {
        show()

        composeRule.onNodeWithTag("ranks.hero.place").assertTextEquals("#2")
        composeRule.onNodeWithTag("ranks.hero.score").assertIsDisplayed()
        composeRule.onNodeWithTag("ranks.hero").assertIsDisplayed()
    }

    @Test
    fun `a board with no place says so in the hero card`() {
        show(position = 2)

        composeRule.onNodeWithTag("ranks.hero.place").assertTextEquals("—")
    }

    @Test
    fun `tapping a game in the list picks that board`() {
        val picked = mutableListOf<RanksBoard>()
        show(onPickBoard = { picked += it })

        composeRule.onNodeWithTag("ranks.list.item.2").performClick()

        assertEquals(listOf<RanksBoard>(RanksBoard.Game(GameId("tango"))), picked)
    }

    @Test
    fun `how ranks work opens from the list`() {
        show()

        composeRule.onNodeWithTag("ranks.list.how").performClick()

        composeRule.onNodeWithTag("ranks.how.sheet").assertIsDisplayed()
    }

    @Test
    fun `tune opens the tune sheet`() {
        show()

        composeRule.onNodeWithTag("ranks.tune").performClick()

        composeRule.onNodeWithTag("ranks.tune.sheet").assertIsDisplayed()
    }
}
