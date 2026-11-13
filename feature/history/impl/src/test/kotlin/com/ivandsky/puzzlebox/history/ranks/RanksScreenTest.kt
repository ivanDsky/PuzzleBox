package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.ranks.OverallStandings
import com.ivandsky.puzzlebox.domain.ranks.OverallState
import com.ivandsky.puzzlebox.game.RankOrder
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
        composeRule.setContent { PuzzleBoxTheme { RanksScreen(RanksUiState(slice, signIn), onSignIn) } }
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

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.onAllNodesWithTagCount(tag: String) =
        onAllNodesWithTag(tag).fetchSemanticsNodes().size
}
