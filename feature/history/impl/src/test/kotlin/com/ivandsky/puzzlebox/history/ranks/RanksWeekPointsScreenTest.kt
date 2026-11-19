package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.history.GOLDEN_PHONE
import com.ivandsky.puzzlebox.resultui.board.BoardRowValue
import com.ivandsky.puzzlebox.resultui.board.DailyBoardRowModel
import com.ivandsky.puzzlebox.resultui.board.DailyBoardState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A game's Week with WeekMonthPoints on (#1698), on a phone: rows and caption read as points over days, not averages. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_PHONE)
class RanksWeekPointsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val rows = listOf(
        DailyBoardRowModel(1, "Bea", 0L, 0, isSelf = false, playerId = "bea", value = BoardRowValue.DayPoints(286.0, days = 5, atLeast = false)),
        DailyBoardRowModel(101, "Ann", 0L, 0, isSelf = true, playerId = "u1", value = BoardRowValue.DayPoints(75.0, days = 1, atLeast = true), beyondPage = true),
    )
    private val week = RanksBoardSlice("Zip · this week", RankOrder.TimeThenHints, DailyBoardState.Ranked(rows), caption = WEEK_MONTH_POINTS_CAPTION)

    private fun show(weekMonthPoints: Boolean = true) {
        val state = RanksUiState(
            week,
            window = RanksSelection.Window.Week,
            places = listOf(BoardPlace(RanksBoard.Overall, "Overall", Standing.Place(1))),
            weekMonthPoints = weekMonthPoints,
        )
        composeRule.setContent { PuzzleBoxTheme { RanksScreen(state, RanksActions(onSignIn = {}, onSwipe = {})) } }
    }

    private fun openHowRanksWork() {
        composeRule.onNodeWithTag("ranks.title").performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.onNodeWithTag("ranks.menu.how").performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
    }

    @Test
    fun `the rows read points over days, at least past the top 100, under the points caption`() {
        show()

        composeRule.onNodeWithText("286 pts").assertIsDisplayed()
        composeRule.onNodeWithText("5 days").assertIsDisplayed()
        composeRule.onAllNodesWithText("≥75 pts").onFirst().assertIsDisplayed()
        composeRule.onAllNodesWithText("1 day").onFirst().assertIsDisplayed()
        composeRule.onAllNodesWithText("100+").onFirst().assertIsDisplayed()
        composeRule.onNodeWithText(WEEK_MONTH_POINTS_CAPTION).assertIsDisplayed()
        composeRule.onAllNodesWithText("avg", substring = true).assertCountEquals(0)
    }

    @Test
    fun `How ranks work says a game's Week and Month add up its days`() {
        show()
        openHowRanksWork()

        composeRule.onNodeWithText("each game adds up your daily scores in the window.", substring = true).assertIsDisplayed()
        composeRule.onAllNodesWithText("the average of the days you played", substring = true).assertCountEquals(0)
    }

    @Test
    fun `How ranks work's Top 100 line says a game's Week and Month total can show at least too`() {
        show()
        openHowRanksWork()

        composeRule.onNodeWithText("your Overall total, or a game's Week and Month total, shows as at least (≥).", substring = true).assertIsDisplayed()
    }

    @Test
    fun `with the flag off How ranks work keeps the average rule`() {
        show(weekMonthPoints = false)
        openHowRanksWork()

        composeRule.onNodeWithText("each game ranks the average of the days you played.", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("past that you see 100+ with no exact place, and your Overall total shows as at least (≥).", substring = true).assertIsDisplayed()
    }
}
