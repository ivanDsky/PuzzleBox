package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.board.DailyBoardRowModel
import com.ivandsky.puzzlebox.gameui.board.DailyBoardState
import com.ivandsky.puzzlebox.history.CalendarState
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A day pick keeps the board on screen until the picked day answers, so the hub changes in place (user-check F9). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RanksHeldBoardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val today = LocalDate.of(2026, 11, 13)
    private val zip = RanksBoard.Game(GameId("zip"))

    private fun places(overall: Standing, game: Standing) =
        listOf(BoardPlace(RanksBoard.Overall, "Overall", overall), BoardPlace(zip, "Zip", game))

    private fun state(title: String, board: DailyBoardState, day: LocalDate, places: List<BoardPlace>, position: Int = 1) = RanksUiState(
        RanksBoardSlice(title, RankOrder.TimeThenHints, board),
        position = position,
        boardCount = 2,
        calendar = CalendarState(shownDay = day, earliestDay = today.minusDays(30), selectedDay = day, today = today),
        places = places,
    )

    private fun ranked(name: String) = DailyBoardState.Ranked(listOf(DailyBoardRowModel(1, name, 62_000L, 0, false, playerId = name)))

    private val drawn = state("Zip · today", ranked("Maya"), today, places(Standing.Place(4), Standing.Place(2)))
    private val loading = state("Zip · Thu, Nov 12", DailyBoardState.Loading, today.minusDays(1), places(Standing.Pending, Standing.Pending))
    private val answered = state("Zip · Thu, Nov 12", ranked("Ann"), today.minusDays(1), places(Standing.Place(7), Standing.Place(1)))

    @Test
    fun `a loading day keeps the drawn board and places, and takes the picked day's everything else`() {
        val held = heldBoard(drawn, loading)

        assertEquals(drawn.board, held.board)
        assertEquals(drawn.places, held.places)
        assertEquals(loading.calendar, held.calendar)
    }

    @Test
    fun `a day that has answered is drawn as it is`() {
        assertSame(answered, heldBoard(drawn, answered))
    }

    @Test
    fun `a place that has answered shows while another still loads`() {
        val partly = answered.copy(places = places(Standing.Pending, Standing.Place(1)))

        assertEquals(places(Standing.Place(4), Standing.Place(1)), heldBoard(drawn, partly).places)
    }

    @Test
    fun `another board is never held, and nothing is held over a board that was loading itself`() {
        assertSame(loading, heldBoard(drawn.copy(position = 0), loading))
        assertSame(loading, heldBoard(loading.copy(calendar = drawn.calendar), loading))
        assertSame(loading, heldBoard(null, loading))
    }

    @Test
    fun `picking a day leaves the board on screen until the day answers, then changes it in place`() {
        var state by mutableStateOf(drawn)
        composeRule.setContent { PuzzleBoxTheme { RanksScreen(state, RanksActions({}, {})) } }

        state = loading
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Maya").assertIsDisplayed()
        composeRule.onNodeWithTag("daily_board.title").assertTextEquals("Zip · today")
        composeRule.onAllNodesWithTag("daily_board.loading").assertCountEquals(0)

        state = answered
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Ann").assertIsDisplayed()
        composeRule.onNodeWithTag("daily_board.title").assertTextEquals("Zip · Thu, Nov 12")
        composeRule.onAllNodesWithTag("daily_board.loading").assertCountEquals(0)
    }

    @Test
    fun `a day that does not answer in time says Loading`() {
        var state by mutableStateOf(drawn)
        composeRule.setContent { PuzzleBoxTheme { RanksScreen(state, RanksActions({}, {})) } }

        state = loading
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(BOARD_HOLD_CAP_MS + 500)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("daily_board.loading").assertIsDisplayed()
        composeRule.onNodeWithTag("daily_board.title").assertTextEquals("Zip · Thu, Nov 12")
    }
}
