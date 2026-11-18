package com.ivandsky.puzzlebox.profile.accounts

import android.app.Application
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.app.screens.ProfileUiState.LinkedInBoards
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInAccountView
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The posting switch and its warning: the warning comes before enable, Cancel is a no-op, off needs no confirmation. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class LinkedInPostingSwitchTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val changes = mutableListOf<Boolean>()
    private val summary = LinkedInAccountView("Mira Voss", null, 142, 5, 213, listOf("Zip"), null)

    private fun setSwitch(checked: Boolean) {
        composeRule.setContent {
            PuzzleBoxTheme { LinkedInPostingSwitch(checked = checked, onCheckedChange = { changes += it }) }
        }
    }

    private fun setRow(state: LinkedInRowState) {
        composeRule.setContent {
            PuzzleBoxTheme {
                LinkedInRow(
                    state = state,
                    nowMs = 0L,
                    onConnect = {},
                    onResume = {},
                    onDisconnect = {},
                    onDeleteSavedBoards = {},
                    boards = LinkedInBoards(canDelete = false),
                    onPostingChange = { changes += it },
                )
            }
        }
    }

    @Test
    fun `turning it on shows the warning first and changes nothing yet`() {
        setSwitch(checked = false)

        composeRule.onNodeWithTag("profile.linkedin.posting").performClick()

        composeRule.onNodeWithTag("linkedin.posting.warning").assertExists()
        assertEquals(emptyList<Boolean>(), changes)
    }

    @Test
    fun `cancel closes the warning and changes nothing`() {
        setSwitch(checked = false)
        composeRule.onNodeWithTag("profile.linkedin.posting").performClick()

        composeRule.onNodeWithTag("linkedin.posting.warning.cancel").performClick()

        composeRule.onNodeWithTag("linkedin.posting.warning").assertDoesNotExist()
        assertEquals(emptyList<Boolean>(), changes)
    }

    @Test
    fun `accept turns it on once and closes the warning`() {
        setSwitch(checked = false)
        composeRule.onNodeWithTag("profile.linkedin.posting").performClick()

        composeRule.onNodeWithTag("linkedin.posting.warning.accept").performClick()

        composeRule.onNodeWithTag("linkedin.posting.warning").assertDoesNotExist()
        assertEquals(listOf(true), changes)
    }

    @Test
    fun `turning it off needs no warning`() {
        setSwitch(checked = true)

        composeRule.onNodeWithTag("profile.linkedin.posting").performClick()

        composeRule.onNodeWithTag("linkedin.posting.warning").assertDoesNotExist()
        assertEquals(listOf(false), changes)
    }

    @Test
    fun `the switch is labelled Experimental and is at least 48dp tall`() {
        setSwitch(checked = false)

        composeRule.onNodeWithText("Post results to LinkedIn").assertExists()
        composeRule.onNodeWithText("Experimental").assertExists()
        composeRule.onNodeWithTag("profile.linkedin.posting").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `the warning says the five risks in plain words`() {
        setSwitch(checked = false)
        composeRule.onNodeWithTag("profile.linkedin.posting").performClick()

        listOf(
            "send your result to LinkedIn as if you played there",
            "breaks LinkedIn's terms more visibly than reading does",
            "up to your LinkedIn settings",
            "never changes them",
            "can't be undone",
            "may restrict your account",
        ).forEach { composeRule.onNodeWithText(it, substring = true).performScrollTo().assertExists() }
    }

    @Test
    fun `a connected row shows the switch`() {
        setRow(LinkedInRowState.Connected(summary))
        composeRule.onNodeWithTag("profile.linkedin.posting").assertExists()
    }

    @Test
    fun `a paused row still shows the switch and lets it turn off`() {
        setRow(LinkedInRowState.Paused(summary.copy(posting = true), "paused", needsReconnect = false))

        composeRule.onNodeWithTag("profile.linkedin.posting").performClick()

        assertEquals(listOf(false), changes)
    }
}
