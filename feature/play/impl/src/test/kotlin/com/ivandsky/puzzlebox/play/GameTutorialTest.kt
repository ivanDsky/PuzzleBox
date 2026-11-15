package com.ivandsky.puzzlebox.play

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The GameKey route's tutorial over stand-in games: a game with unseen lessons
 * plays them before its board, finishing or skipping marks it seen, a seen game or one
 * without lessons shows the board at once, and How to play reopens the lessons over the
 * live play with its clock folded until the board returns. Lessons run in a sandbox:
 * nothing reaches the slot's play, its save or the records. Back leaves them unseen.
 * The seen-read that lands late is covered by [GameTutorialPendingReadTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GameTutorialTest : GameTutorialFixture() {

    @Test
    fun `a first play runs the lessons, then the board, and marks the game seen`() {
        fakes[alpha].teach(2)
        val prefs = FakeUserPreferencesRepository()
        start(prefs)

        composeRule.onNodeWithTag("tutorial.host").assertExists()
        composeRule.onNodeWithTag("play.how_to_play").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Step 1 of 2").assertExists()

        winLesson()
        composeRule.onNodeWithContentDescription("Step 2 of 2").assertExists()
        winLesson()
        composeRule.onNodeWithTag("tutorial.ready").assertExists()
        assertTrue(prefs.seen.isEmpty())

        click("tutorial.finish")
        composeRule.onNodeWithTag("tutorial.host").assertDoesNotExist()
        composeRule.onNodeWithText("alpha board 0").assertExists()
        assertEquals(setOf(alpha), prefs.seen)
        // The lessons' wins were the sandbox's: no record, no handoff, the slot's play untouched.
        assertTrue(records().isEmpty())
        assertEquals(0, solved)
        assertEquals(0, (live().controller as FakeController).moves)
    }

    @Test
    fun `the clock stays folded behind the first-play lessons and runs once the board shows`() {
        fakes[alpha].teach(1)
        start(FakeUserPreferencesRepository())
        val play = live().controller as FakeController

        composeRule.onNodeWithTag("tutorial.host").assertExists()
        assertNull("lesson time never counts", play.clockWindow)
        composeRule.runOnIdle { play.advance(10_000L) }
        assertEquals(0L, play.displayedElapsedMs())

        click("tutorial.skip")

        composeRule.onNodeWithText("alpha board 0").assertExists()
        assertNotNull("the board's clock runs without a move", play.clockWindow)
    }

    @Test
    fun `skip marks the game seen and shows its board`() {
        fakes[alpha].teach(3)
        val prefs = FakeUserPreferencesRepository()
        start(prefs)

        click("tutorial.skip")

        composeRule.onNodeWithTag("tutorial.host").assertDoesNotExist()
        composeRule.onNodeWithText("alpha board 0").assertExists()
        assertEquals(setOf(alpha), prefs.seen)
    }

    @Test
    fun `a seen game shows its board at once and offers how to play`() {
        fakes[alpha].teach(2)
        start(FakeUserPreferencesRepository(seen = setOf(alpha)))

        composeRule.onNodeWithTag("tutorial.host").assertDoesNotExist()
        composeRule.onNodeWithText("alpha board 0").assertExists()
        composeRule.onNodeWithTag("play.how_to_play").assertExists()
    }

    @Test
    fun `a game without lessons shows its board and offers no how to play`() {
        start(FakeUserPreferencesRepository(), game = beta)

        composeRule.onNodeWithText("beta board 0").assertExists()
        composeRule.onNodeWithTag("tutorial.host").assertDoesNotExist()
        composeRule.onNodeWithTag("play.how_to_play").assertDoesNotExist()
    }

    @Test
    fun `how to play folds the live clock, keeps the play and resumes where it was`() {
        fakes[alpha].teach(2)
        start(FakeUserPreferencesRepository(seen = setOf(alpha)))
        val controller = fakes[alpha].latest
        composeRule.runOnIdle { controller.move() }
        composeRule.waitForIdle()
        composeRule.runOnIdle { controller.advance(5_000L) }

        click("play.how_to_play")
        composeRule.onNodeWithTag("tutorial.host").assertExists()
        assertNull(controller.clockWindow)
        composeRule.runOnIdle { controller.advance(10_000L) }
        assertEquals(5_000L, controller.displayedElapsedMs())

        winLesson()
        click("tutorial.skip")

        composeRule.onNodeWithText("alpha board 1").assertExists()
        assertSame(controller, live().controller)
        assertNotNull(controller.clockWindow)
        composeRule.runOnIdle { controller.advance(1_000L) }
        assertEquals(6_000L, controller.displayedElapsedMs())
        assertTrue(records().isEmpty())
        assertEquals(0, solved)
    }

    @Test
    fun `a recomposed entry keeps the open lesson step`() {
        fakes[alpha].teach(3)
        start(FakeUserPreferencesRepository())
        winLesson()
        composeRule.onNodeWithContentDescription("Step 2 of 3").assertExists()

        composeRule.runOnIdle { entry.intValue++ }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Step 2 of 3").assertExists()
    }

    @Test
    fun `up during the first-play lessons leaves the game with them unseen`() {
        fakes[alpha].teach(2)
        val prefs = FakeUserPreferencesRepository()
        start(prefs)

        click("game.up")

        assertEquals(1, backs)
        assertTrue(prefs.seen.isEmpty())
    }
}
