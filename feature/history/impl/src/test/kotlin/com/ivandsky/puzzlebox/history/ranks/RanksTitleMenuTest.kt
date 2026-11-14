package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.board.DailyBoardState
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The title menu, How ranks work and the swipe nudge as drawn on the hub. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RanksTitleMenuTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val zip = RanksBoard.Game(GameId("zip"))
    private val places = listOf(
        BoardPlace(RanksBoard.Overall, "Overall", Standing.Place(12)),
        BoardPlace(zip, "Zip", Standing.Place(3)),
        BoardPlace(RanksBoard.Game(GameId("tango")), "Tango", Standing.Beyond),
        BoardPlace(RanksBoard.Game(GameId("queens")), "Queens", Standing.None),
        BoardPlace(RanksBoard.Game(GameId("strands")), "Strands", Standing.Pending),
    )
    private val slice = RanksBoardSlice("Overall · today", RankOrder.TimeThenHints, DailyBoardState.Unavailable)

    private fun show(
        state: RanksUiState = RanksUiState(slice, places = places, boardCount = places.size),
        onPick: (RanksBoard) -> Unit = {},
        onNudgeShown: () -> Unit = {},
    ) {
        composeRule.setContent { PuzzleBoxTheme { RanksScreen(state, RanksActions(onSignIn = {}, onSwipe = {}, onPickBoard = onPick, onNudgeShown = onNudgeShown)) } }
    }

    @Test
    fun `the title names the current board and opens a menu of every board with your place`() {
        show()

        composeRule.onNodeWithTag("ranks.title").assertIsDisplayed()
        composeRule.onNodeWithTag("ranks.title").performClick()
        composeRule.mainClock.advanceTimeBy(1_000)

        places.forEachIndexed { index, place ->
            composeRule.onNodeWithTag("ranks.menu.item.$index").assertIsDisplayed()
            composeRule.onNodeWithText(place.standing.label()).assertIsDisplayed()
        }
        composeRule.onNodeWithTag("ranks.menu.how").assertIsDisplayed()
    }

    @Test
    fun `picking a board reports it and closes the menu`() {
        var picked: RanksBoard? = null
        show(onPick = { picked = it })

        composeRule.onNodeWithTag("ranks.title").performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.onNodeWithTag("ranks.menu.item.1").performClick()
        composeRule.mainClock.advanceTimeBy(1_000)

        assertEquals(zip, picked)
        composeRule.onNodeWithTag("ranks.menu").assertDoesNotExist()
    }

    @Test
    fun `How ranks work opens the rule sheet`() {
        show()

        composeRule.onNodeWithTag("ranks.title").performClick()
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.onNodeWithTag("ranks.menu.how").performClick()
        composeRule.mainClock.advanceTimeBy(1_000)

        composeRule.onNodeWithTag("ranks.how.sheet").assertIsDisplayed()
        composeRule.onNodeWithText(RANKS_RULE).assertIsDisplayed()
    }

    @Test
    fun `the nudge comes in after its delay, then reports it was shown`() {
        var shown = 0
        composeRule.mainClock.autoAdvance = false
        show(RanksUiState(slice, places = places, boardCount = places.size, nudge = true), onNudgeShown = { shown++ })

        composeRule.mainClock.advanceTimeBy(500)
        composeRule.onNodeWithTag("ranks.nudge").assertDoesNotExist()

        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.onNodeWithTag("ranks.nudge").assertTextEquals("Swipe for Zip ›")
        assertEquals(0, shown)

        composeRule.mainClock.advanceTimeBy(4_000)
        assertEquals(1, shown)
    }

    @Test
    fun `no nudge once it was shown`() {
        composeRule.mainClock.autoAdvance = false
        show(RanksUiState(slice, places = places, boardCount = places.size, nudge = false))

        composeRule.mainClock.advanceTimeBy(6_000)

        composeRule.onNodeWithTag("ranks.nudge").assertDoesNotExist()
    }

    @Test
    fun `the hint names the next board, the previous one from the last, and nothing for a lone board`() {
        assertEquals("Swipe for Zip ›", nudgeText(places, 0))
        assertEquals("Swipe for Queens ›", nudgeText(places, 2))
        assertEquals("‹ Swipe for Queens", nudgeText(places, 4))
        assertNull(nudgeText(places.take(1), 0))
        assertNull(nudgeText(places, 9))
    }

    @Test
    fun `the dot's pulse swells and settles back`() {
        assertEquals(1f, dotPulseScale(0f), 0.001f)
        assertEquals(true, dotPulseScale(0.5f) > 1.5f)
        assertEquals(1f, dotPulseScale(1f), 0.001f)
    }
}
