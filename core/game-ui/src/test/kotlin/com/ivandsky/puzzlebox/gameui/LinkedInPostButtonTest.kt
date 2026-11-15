package com.ivandsky.puzzlebox.gameui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Post to LinkedIn button: each state's words and tap rule, its 48 dp height, and its place in the actions row. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class LinkedInPostButtonTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var taps = 0

    private val words = mapOf(
        LinkedInPostButtonState.Post to "Post to LinkedIn",
        LinkedInPostButtonState.Posting to "Posting…",
        LinkedInPostButtonState.Posted to "Posted to LinkedIn",
        LinkedInPostButtonState.AlreadyOnLinkedIn to "Already on LinkedIn",
        LinkedInPostButtonState.Check to "Not sure it reached LinkedIn. Tap to check.",
        LinkedInPostButtonState.Retry to "Couldn't reach LinkedIn. Try again.",
        LinkedInPostButtonState.Paused to "LinkedIn paused — open Profile to resume",
        LinkedInPostButtonState.NotToday to "That puzzle is no longer today's",
    )

    private fun show(state: LinkedInPostButtonState) {
        composeRule.setContent { PuzzleBoxTheme { LinkedInPostButton(state, onClick = { taps++ }) } }
    }

    @Test
    fun `every state has its words`() {
        assertEquals(LinkedInPostButtonState.entries.toSet(), words.keys)
        for ((state, label) in words) assertEquals(label, state.label)
    }

    @Test
    fun `the button shows its label and is at least 48 dp tall`() {
        var shown by mutableStateOf(LinkedInPostButtonState.Post)
        composeRule.setContent { PuzzleBoxTheme { LinkedInPostButton(shown, onClick = {}) } }
        for ((state, label) in words) {
            shown = state
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("result.linkedin_post").assertTextEquals(label).assertHeightIsAtLeast(48.dp)
        }
    }

    @Test
    fun `only Post, Check and Retry take a tap`() {
        val tappable = setOf(LinkedInPostButtonState.Post, LinkedInPostButtonState.Check, LinkedInPostButtonState.Retry)
        assertEquals(tappable, LinkedInPostButtonState.entries.filter { it.enabled }.toSet())
    }

    @Test
    fun `a tappable state calls back`() {
        show(LinkedInPostButtonState.Post)
        composeRule.onNodeWithTag("result.linkedin_post").assertIsEnabled().performClick()
        assertEquals(1, taps)
    }

    @Test
    fun `a status state is disabled and does not call back`() {
        show(LinkedInPostButtonState.Posted)
        composeRule.onNodeWithTag("result.linkedin_post").assertIsNotEnabled().performClick()
        assertEquals(0, taps)
    }

    @Test
    fun `the actions row carries the button only when given one`() {
        composeRule.setContent {
            PuzzleBoxTheme {
                ResultHeroActions(ResultPrimaryAction.Done, onPrimary = {}, onShare = {}, onPlayAgain = {})
            }
        }
        composeRule.onNodeWithTag("result.done").assertExists()
        composeRule.onNodeWithTag("result.linkedin_post").assertDoesNotExist()
    }

    @Test
    fun `the actions row shows the button above the primary and taps it`() {
        composeRule.setContent {
            PuzzleBoxTheme {
                ResultHeroActions(
                    ResultPrimaryAction.Done,
                    onPrimary = {},
                    onShare = {},
                    onPlayAgain = {},
                    linkedInPost = LinkedInPostButtonState.Post,
                    onLinkedInPost = { taps++ },
                )
            }
        }
        composeRule.onNodeWithTag("result.done").assertExists()
        composeRule.onNodeWithTag("result.linkedin_post").performClick()
        assertEquals(1, taps)
    }
}
