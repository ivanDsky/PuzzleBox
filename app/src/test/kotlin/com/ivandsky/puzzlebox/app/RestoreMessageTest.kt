package com.ivandsky.puzzlebox.app

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import com.ivandsky.puzzlebox.data.cloud.RestoreSummary
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.testing.cloud.FakeCloudSync
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    private fun counts(games: Int = 0, drafts: Int = 0, boardDays: Int = 0, published: Int = 0) = RestoreSummary(
        mapOf("completions" to games, "drafts" to drafts, "boards" to boardDays, "published" to published),
    )

    private fun setShell(restored: kotlinx.coroutines.flow.Flow<RestoreSummary>?) {
        composeRule.setContent { PuzzleBoxTheme { RestoreWelcome(restored) } }
    }

    @Test
    fun `a restore shows the welcome with its counts`() {
        setShell(cloud.restored)
        cloud.emitRestored(counts(games = 12, drafts = 3, boardDays = 7))
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Welcome back — 12 games, 3 drafts, 7 board days restored").assertExists()
    }

    @Test
    fun `a restore of one kind shows only that part, in the singular for one`() {
        assertEquals("Welcome back — 1 draft restored", restoreWelcomeText(counts(drafts = 1)))
        assertEquals("Welcome back — 1 game, 12 board days restored", restoreWelcomeText(counts(games = 1, boardDays = 12)))
        assertEquals("Welcome back — 3 games, 1 draft restored", restoreWelcomeText(counts(games = 3, drafts = 1)))
        assertEquals("Welcome back — 2 drafts, 1 board day restored", restoreWelcomeText(counts(drafts = 2, boardDays = 1)))
    }

    @Test
    fun `a restore with no games, drafts or board days has no welcome`() {
        assertNull(restoreWelcomeText(counts()))
        assertNull(restoreWelcomeText(counts(published = 5)))
        assertNull(restoreWelcomeText(RestoreSummary(emptyMap())))
        assertEquals("Welcome back — 2 games restored", restoreWelcomeText(counts(games = 2, published = 5)))
    }

    @Test
    fun `a summary with nothing to welcome shows no message`() {
        setShell(cloud.restored)
        cloud.emitRestored(counts(published = 4))
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("shell.restore.welcome").assertDoesNotExist()
    }

    @Test
    fun `the message shows once and goes away without coming back`() {
        setShell(cloud.restored)
        cloud.emitRestored(counts(games = 1, boardDays = 2))
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
        cloud.emitRestored(counts(games = 1, drafts = 1, boardDays = 1))
        cloud.emitRestored(counts(games = 2, drafts = 2, boardDays = 2))
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Welcome back — 1 game, 1 draft, 1 board day restored").assertExists()
        composeRule.onNodeWithText("Welcome back — 2 games, 2 drafts, 2 board days restored").assertDoesNotExist()

        // Just past one message's 4 s, so the second is up and not yet gone.
        composeRule.mainClock.advanceTimeBy(4_500)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Welcome back — 1 game, 1 draft, 1 board day restored").assertDoesNotExist()
        composeRule.onNodeWithText("Welcome back — 2 games, 2 drafts, 2 board days restored").assertExists()

        composeRule.mainClock.advanceTimeBy(10_000)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("shell.restore.welcome").assertDoesNotExist()
    }

    @Test
    fun `with no cloud sync nothing is shown`() {
        setShell(null)
        cloud.emitRestored(counts(games = 12, drafts = 3, boardDays = 7))
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("shell.restore.welcome").assertDoesNotExist()
    }
}
