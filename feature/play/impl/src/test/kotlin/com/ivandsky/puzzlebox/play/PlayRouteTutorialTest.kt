package com.ivandsky.puzzlebox.play

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeKeptSessionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tutorials on the play route (spec A11): the first pick of a game with unseen lessons
 * plays them before its board, How to play reopens them over the live play with its
 * clock paused, and neither keeps nor records anything. The view model holds the open
 * tutorial, so a re-entry keeps its step.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayRouteTutorialTest : PlayRouteHarness() {

    /** Wins the open lesson and waits out its success beat. */
    private fun winLesson() {
        composeRule.runOnIdle { fakes[alpha].latest.win() }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(PAST_THE_BEAT_MS)
        composeRule.waitForIdle()
    }

    private fun click(tag: String) {
        composeRule.onNodeWithTag(tag).performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `a first pick plays the lessons, then the board, and marks the game seen`() {
        fakes[alpha].teach(2)
        val daily = freshDaily()
        val prefs = FakeUserPreferencesRepository()
        val completions = FakeCompletionRepository()
        koinWith(daily = daily, completions = completions, prefs = prefs)
        setContent()

        pick(alpha)
        composeRule.onNodeWithTag("tutorial.host").assertExists()
        composeRule.onNodeWithContentDescription("Step 1 of 2").assertExists()
        assertEquals(0, daily.todayCalls(alpha))
        assertNull(store().active.value)

        winLesson()
        composeRule.onNodeWithContentDescription("Step 2 of 2").assertExists()
        winLesson()
        composeRule.onNodeWithTag("tutorial.ready").assertExists()
        assertTrue(prefs.seen.isEmpty())

        click("tutorial.finish")
        composeRule.onNodeWithText("alpha board 0").assertExists()
        assertEquals(setOf(alpha), prefs.seen)
        assertEquals(1, daily.todayCalls(alpha))
        assertTrue(handedOff.isEmpty())
        assertTrue(runBlocking { completions.observeRecords().first() }.isEmpty())

        pressBack()
        pick(alpha)
        composeRule.onNodeWithTag("tutorial.host").assertDoesNotExist()
        composeRule.onNodeWithText("alpha board 0").assertExists()
    }

    @Test
    fun `skip on a first pick marks the game seen and shows its board`() {
        fakes[alpha].teach(3)
        val prefs = FakeUserPreferencesRepository()
        koinWith(prefs = prefs)
        setContent()

        pick(alpha)
        click("tutorial.skip")

        composeRule.onNodeWithTag("tutorial.host").assertDoesNotExist()
        composeRule.onNodeWithText("alpha board 0").assertExists()
        assertEquals(setOf(alpha), prefs.seen)
    }

    @Test
    fun `a game without lessons offers no how to play`() {
        koinWith()
        setContent()
        pick(beta)
        composeRule.onNodeWithText("beta board 0").assertExists()
        composeRule.onNodeWithTag("play.how_to_play").assertDoesNotExist()
    }

    @Test
    fun `how to play pauses the live clock, keeps nothing and resumes where it was`() {
        fakes[alpha].teach(2)
        val kept = FakeKeptSessionRepository()
        val completions = FakeCompletionRepository()
        koinWith(completions = completions, kept = kept, prefs = FakeUserPreferencesRepository(seen = setOf(alpha)))
        setContent()
        pick(alpha)
        val live = fakes[alpha].latest
        composeRule.runOnIdle { live.move() }
        composeRule.runOnIdle { live.advance(5_000L) }

        click("play.how_to_play")
        composeRule.onNodeWithTag("tutorial.host").assertExists()
        assertNull(live.clockWindow)
        composeRule.runOnIdle { live.advance(10_000L) }
        assertEquals(5_000L, live.displayedElapsedMs())

        // A lesson's win is the sandbox's own: no record, no handoff, the live play untouched.
        winLesson()
        click("tutorial.skip")

        composeRule.onNodeWithText("alpha board 1").assertExists()
        assertEquals(live, store().active.value!!.controller)
        assertNotNull(live.clockWindow)
        assertEquals(5_000L, live.displayedElapsedMs())
        composeRule.runOnIdle { live.advance(1_000L) }
        assertEquals(6_000L, live.displayedElapsedMs())
        assertEquals(1, live.moves)
        assertTrue(kept.current.isEmpty())
        assertTrue(handedOff.isEmpty())
        assertTrue(runBlocking { completions.observeRecords().first() }.isEmpty())
    }

    @Test
    fun `a re-entry keeps the open lesson step`() {
        fakes[alpha].teach(3)
        val daily: FakeDailyRepository = freshDaily()
        koinWith(daily = daily)
        setContent()
        pick(alpha)
        winLesson()
        composeRule.onNodeWithContentDescription("Step 2 of 3").assertExists()

        composeRule.runOnIdle { entry++ }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Step 2 of 3").assertExists()
        assertNull(store().active.value)
        assertEquals(0, daily.todayCalls(alpha))
    }

    @Test
    fun `back during the first-play lessons shows the picker and leaves them unseen`() {
        fakes[alpha].teach(2)
        val prefs = FakeUserPreferencesRepository()
        koinWith(prefs = prefs)
        var back = 0
        setContent(onBack = { back++ })
        pick(alpha)

        pressBack()

        assertEquals(1, back)
        composeRule.onNodeWithTag("play.picker").assertExists()
        assertTrue(prefs.seen.isEmpty())
    }

    private companion object {
        /** Longer than the tutorial's success beat between lessons. */
        const val PAST_THE_BEAT_MS = 1_000L
    }
}
