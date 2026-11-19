package com.ivandsky.puzzlebox.resultui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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

/** The Post to LinkedIn row: each state's title and subtitle, its tap rule, its 56 dp height and its pending guard. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class LinkedInPostRowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var taps = 0

    private val titles = mapOf(
        LinkedInPostRowState.Post to "Post to LinkedIn",
        LinkedInPostRowState.Posting to "Posting to LinkedIn",
        LinkedInPostRowState.Posted to "Posted to LinkedIn",
        LinkedInPostRowState.AlreadyOnLinkedIn to "Already on LinkedIn",
        LinkedInPostRowState.Check to "Check LinkedIn",
        LinkedInPostRowState.Retry to "Post again",
        LinkedInPostRowState.Paused to "LinkedIn paused",
        LinkedInPostRowState.NotToday to "Not today's puzzle",
        LinkedInPostRowState.Tutorial to "Tutorial on LinkedIn today",
    )

    private val subtitles = mapOf(
        LinkedInPostRowState.Post to "0:58 isn't on LinkedIn yet",
        LinkedInPostRowState.Posting to "Sending your 0:58…",
        LinkedInPostRowState.Posted to "Your 0:58 is on LinkedIn",
        LinkedInPostRowState.AlreadyOnLinkedIn to "LinkedIn has a result for this puzzle",
        LinkedInPostRowState.Check to "Not sure it reached LinkedIn. Tap to check.",
        LinkedInPostRowState.Retry to "Couldn't reach LinkedIn",
        LinkedInPostRowState.Paused to "Open Profile to resume",
        LinkedInPostRowState.NotToday to "Only today's puzzle can be posted",
        LinkedInPostRowState.Tutorial to "Today's puzzle can't be posted from this account",
    )

    private fun show(state: LinkedInPostRowState, time: String? = "0:58", pending: Boolean = false) {
        composeRule.setContent {
            PuzzleBoxTheme { LinkedInPostRow(state, time, onClick = { taps++ }, pending = pending) }
        }
    }

    @Test
    fun `every state has its title and subtitle`() {
        assertEquals(LinkedInPostRowState.entries.toSet(), titles.keys)
        for ((state, title) in titles) {
            assertEquals(title, state.title)
            assertEquals(subtitles.getValue(state), state.subtitle("0:58"))
        }
    }

    @Test
    fun `without a time the subtitles say your time`() {
        assertEquals("Your time isn't on LinkedIn yet", LinkedInPostRowState.Post.subtitle(null))
        assertEquals("Sending your time…", LinkedInPostRowState.Posting.subtitle(null))
        assertEquals("Your time is on LinkedIn", LinkedInPostRowState.Posted.subtitle(null))
    }

    @Test
    fun `the row shows its title and subtitle and is at least 56 dp tall`() {
        var shown by mutableStateOf(LinkedInPostRowState.Post)
        composeRule.setContent { PuzzleBoxTheme { LinkedInPostRow(shown, "0:58", onClick = {}) } }
        for (state in LinkedInPostRowState.entries) {
            shown = state
            composeRule.waitForIdle()
            composeRule.onNodeWithTag("result.linkedin_post").assertHeightIsAtLeast(56.dp)
            composeRule.onNodeWithText(titles.getValue(state)).assertExists()
            composeRule.onNodeWithText(subtitles.getValue(state)).assertExists()
        }
    }

    @Test
    fun `only Post, Check and Retry take a tap`() {
        val tappable = setOf(LinkedInPostRowState.Post, LinkedInPostRowState.Check, LinkedInPostRowState.Retry)
        assertEquals(tappable, LinkedInPostRowState.entries.filter { it.enabled }.toSet())
    }

    @Test
    fun `a tappable state calls back once per tap`() {
        show(LinkedInPostRowState.Post)
        composeRule.onNodeWithTag("result.linkedin_post").assertIsEnabled().performClick()
        assertEquals(1, taps)
    }

    @Test
    fun `a status state is disabled and does not call back`() {
        show(LinkedInPostRowState.Posted)
        composeRule.onNodeWithTag("result.linkedin_post").assertIsNotEnabled().performClick()
        assertEquals(0, taps)
    }

    @Test
    fun `a pending row is inert`() {
        show(LinkedInPostRowState.Post, pending = true)
        composeRule.onNodeWithTag("result.linkedin_post").performClick()
        assertEquals(0, taps)
    }
}
