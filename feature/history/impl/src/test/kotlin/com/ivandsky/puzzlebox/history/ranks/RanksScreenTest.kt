package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.ranks.OverallStandings
import com.ivandsky.puzzlebox.domain.ranks.OverallState
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.board.DailyBoardRowModel
import com.ivandsky.puzzlebox.gameui.board.DailyBoardState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The hub's board slot as drawn: Overall's points, the partial note, and the in-place sign-in (A3, A11, A12). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class RanksScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val standings = OverallStandings(
        listOf(
            OverallStandings.Row("maya", "Maya", 1, 200.0, 2, truncated = false),
            OverallStandings.Row("u1", "Ann", 2, 150.0, 2, truncated = true),
        ),
    )

    private fun show(state: OverallState, signIn: RanksSignIn = RanksSignIn.Idle, onSignIn: () -> Unit = {}) {
        val slice = RanksBoardSlice("Overall · today", RankOrder.TimeThenHints, state.toDailyBoardState("u1"), "Points", state.boardNote())
        composeRule.setContent { PuzzleBoxTheme { RanksScreen(RanksUiState(slice, signIn), onSignIn, onSwipe = {}) } }
    }

    @Test
    fun `a partial Overall shows its rows and the k of n note`() {
        show(OverallState.Partial(standings, loaded = 2, total = 5))

        composeRule.onNodeWithText("Maya").assertIsDisplayed()
        composeRule.onNodeWithTag("ranks.board.note").assertIsDisplayed()
        composeRule.onNodeWithText("2 of 5 boards").assertIsDisplayed()
    }

    @Test
    fun `a complete Overall has no note`() {
        show(OverallState.Complete(standings))

        composeRule.onNodeWithText("Ann").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithTagCount("ranks.board.note"))
    }

    @Test
    fun `signed out shows the sign-in prompt and its button signs in`() {
        var taps = 0
        show(OverallState.SignedOut, onSignIn = { taps++ })

        composeRule.onNodeWithTag("ranks.signIn").assertIsDisplayed()
        composeRule.onNodeWithTag("ranks.signIn.button").performClick()

        assertEquals(1, taps)
    }

    @Test
    fun `while signing in the button is off`() {
        show(OverallState.SignedOut, signIn = RanksSignIn.SigningIn)

        composeRule.onNodeWithTag("ranks.signIn.button").assertIsNotEnabled()
    }

    @Test
    fun `a failed sign-in says so`() {
        show(OverallState.SignedOut, signIn = RanksSignIn.Failed)

        composeRule.onNodeWithTag("ranks.signIn.failed").assertIsDisplayed()
    }

    // The hub goes to the board a swipe asks for, as the view model has it: the next swipe starts from there.
    private fun showSwiping(position: Int = 1, onRefresh: (() -> Unit)? = null, onSwipe: (Int) -> Unit) {
        val slice = RanksBoardSlice("Zip · today", RankOrder.TimeThenHints, DailyBoardState.Ranked(listOf(DailyBoardRowModel(1, "Maya", 62_000L, 0, false, playerId = "maya"))))
        var at by mutableIntStateOf(position)
        composeRule.setContent {
            PuzzleBoxTheme {
                RanksScreen(
                    RanksUiState(slice, position = at, boardCount = 4),
                    {},
                    onSwipe = {
                        at += it
                        onSwipe(it)
                    },
                    onRefresh = onRefresh,
                )
            }
        }
    }

    @Test
    fun `swiping the board left asks for the next board and right for the previous`() {
        val steps = mutableListOf<Int>()
        showSwiping { steps += it }

        composeRule.onNodeWithTag("ranks.board").performTouchInput { swipeLeft() }
        composeRule.onNodeWithTag("ranks.board").performTouchInput { swipeRight() }

        assertEquals(listOf(1, -1), steps)
    }

    @Test
    fun `a short slow drag leaves the board where it is`() {
        val steps = mutableListOf<Int>()
        showSwiping { steps += it }

        composeRule.onNodeWithTag("ranks.board").performTouchInput {
            swipe(Offset(centerX, centerY), Offset(centerX - width * 0.1f, centerY), durationMillis = 1_500)
        }

        assertEquals(emptyList<Int>(), steps)
    }

    @Test
    fun `a swipe in the empty space under a short board changes the board too`() {
        val steps = mutableListOf<Int>()
        showSwiping { steps += it }

        composeRule.onNodeWithTag("route.ranks").performTouchInput {
            swipe(Offset(right - 10f, bottom - 10f), Offset(left + 10f, bottom - 10f))
        }

        assertEquals(listOf(1), steps)
    }

    @Test
    fun `a slanted sideways swipe changes the board and pulls no refresh`() {
        val steps = mutableListOf<Int>()
        var refreshes = 0
        showSwiping(onRefresh = { refreshes++ }) { steps += it }

        // 35° off the horizontal, and 300 px down: past the pull's threshold had the page taken it.
        composeRule.onNodeWithTag("ranks.board").performTouchInput {
            down(center)
            repeat(15) { moveBy(Offset(-30f, 21f)) }
            up()
        }
        composeRule.waitForIdle()

        assertEquals(listOf(1), steps)
        assertEquals(0, refreshes)
    }

    @Test
    fun `a pull down that drifts sideways refreshes and leaves the board where it is`() {
        val steps = mutableListOf<Int>()
        var refreshes = 0
        showSwiping(onRefresh = { refreshes++ }) { steps += it }

        composeRule.onNodeWithTag("ranks.board").performTouchInput {
            down(center)
            repeat(8) { moveBy(Offset(2f, 30f)) }
            repeat(10) { moveBy(Offset(40f, 0f)) }
            up()
        }
        composeRule.waitForIdle()

        assertEquals(emptyList<Int>(), steps)
        assertEquals(1, refreshes)
    }

    @Test
    fun `the dots say which board of how many`() {
        showSwiping(position = 1) {}

        composeRule.onNodeWithTag("ranks.dots").assertContentDescriptionEquals("Board 2 of 4")
    }

    @Test
    fun `a single board shows no dots`() {
        composeRule.setContent {
            PuzzleBoxTheme { RanksScreen(RanksUiState(RanksBoardSlice("Zip · today", RankOrder.TimeThenHints, DailyBoardState.Loading)), {}, {}) }
        }

        assertEquals(0, composeRule.onAllNodesWithTagCount("ranks.dots"))
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTagCount(tag: String) =
        onAllNodesWithTag(tag).fetchSemanticsNodes().size
}
