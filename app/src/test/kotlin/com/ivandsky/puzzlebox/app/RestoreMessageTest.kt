package com.ivandsky.puzzlebox.app

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import com.ivandsky.puzzlebox.data.cloud.RestoreSummary
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.testing.cloud.FakeCloudSync
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A33: the first full restore on a device shows "Welcome back" once; with no cloud sync bound the shell shows nothing. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
class RestoreMessageTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val cloud = FakeCloudSync()

    private fun setShell(restored: kotlinx.coroutines.flow.Flow<RestoreSummary>?) {
        composeRule.setContent { PuzzleBoxTheme { RestoreWelcome(restored) } }
    }

    @Test
    fun `a restore shows the welcome with its three counts`() {
        setShell(cloud.restored)
        cloud.emitRestored(RestoreSummary(games = 12, drafts = 3, boardDays = 7))
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Welcome back — 12 games, 3 drafts, 7 board days restored").assertExists()
    }

    @Test
    fun `the message shows once and goes away without coming back`() {
        setShell(cloud.restored)
        cloud.emitRestored(RestoreSummary(games = 1, drafts = 0, boardDays = 2))
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("shell.restore.welcome").assertExists()

        composeRule.mainClock.advanceTimeBy(10_000)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("shell.restore.welcome").assertDoesNotExist()

        composeRule.mainClock.advanceTimeBy(10_000)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("shell.restore.welcome").assertDoesNotExist()
    }

    @Test
    fun `two summaries show one message each, one after the other`() {
        setShell(cloud.restored)
        cloud.emitRestored(RestoreSummary(games = 1, drafts = 1, boardDays = 1))
        cloud.emitRestored(RestoreSummary(games = 2, drafts = 2, boardDays = 2))
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Welcome back — 1 games, 1 drafts, 1 board days restored").assertExists()
        composeRule.onNodeWithText("Welcome back — 2 games, 2 drafts, 2 board days restored").assertDoesNotExist()

        // Just past one message's 4 s, so the second is up and not yet gone.
        composeRule.mainClock.advanceTimeBy(4_500)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Welcome back — 1 games, 1 drafts, 1 board days restored").assertDoesNotExist()
        composeRule.onNodeWithText("Welcome back — 2 games, 2 drafts, 2 board days restored").assertExists()

        composeRule.mainClock.advanceTimeBy(10_000)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("shell.restore.welcome").assertDoesNotExist()
    }

    @Test
    fun `with no cloud sync nothing is shown`() {
        setShell(null)
        cloud.emitRestored(RestoreSummary(games = 12, drafts = 3, boardDays = 7))
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("shell.restore.welcome").assertDoesNotExist()
    }
}
