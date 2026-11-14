package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.board.DailyBoardState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The replay opt-in sheet from plain state, with no view model behind it: shown, accepted and dismissed, on its own and
 * as the hub hosts it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ReplayOptInSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val hub = RanksUiState(RanksBoardSlice("Zip · today", RankOrder.TimeThenHints, DailyBoardState.Loading))
    private val answers = mutableListOf<String>()

    private fun show() {
        composeRule.setContent { PuzzleBoxTheme { ReplayOptInSheet(onShare = { answers += "share" }, onKeepPrivate = { answers += "private" }) } }
    }

    private fun showInHub(ask: Boolean) {
        composeRule.setContent {
            PuzzleBoxTheme {
                RanksScreen(
                    hub,
                    onSignIn = {},
                    onSwipe = {},
                    replayOptIn = ask,
                    onShareReplays = { answers += "share" },
                    onKeepReplaysPrivate = { answers += "private" },
                )
            }
        }
    }

    private fun swipeAway() {
        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss)).performSemanticsAction(SemanticsActions.Dismiss)
        composeRule.waitForIdle()
    }

    @Test
    fun `the sheet asks whether to share replays`() {
        show()

        composeRule.onNodeWithTag("ranks.optin.title").assertTextEquals("Share your replays?")
    }

    @Test
    fun `Share my replays accepts`() {
        show()

        composeRule.onNodeWithTag("ranks.optin.share").performClick()

        assertEquals(listOf("share"), answers)
    }

    @Test
    fun `Keep private declines`() {
        show()

        composeRule.onNodeWithTag("ranks.optin.private").performClick()

        assertEquals(listOf("private"), answers)
    }

    @Test
    fun `swiping the sheet away keeps replays private`() {
        show()

        swipeAway()

        assertEquals(listOf("private"), answers)
    }

    @Test
    fun `the hub shows the sheet while its state asks`() {
        showInHub(ask = true)

        composeRule.onNodeWithTag("ranks.optin.sheet").assertIsDisplayed()
    }

    @Test
    fun `the hub shows no sheet while its state does not ask`() {
        showInHub(ask = false)

        assertEquals(0, composeRule.onAllNodesWithTag("ranks.optin.sheet").fetchSemanticsNodes().size)
    }

    @Test
    fun `Share my replays in the hub's sheet reports the share`() {
        showInHub(ask = true)

        composeRule.onNodeWithTag("ranks.optin.share").performClick()

        assertEquals(listOf("share"), answers)
    }

    @Test
    fun `Keep private in the hub's sheet reports keep private`() {
        showInHub(ask = true)

        composeRule.onNodeWithTag("ranks.optin.private").performClick()

        assertEquals(listOf("private"), answers)
    }
}
