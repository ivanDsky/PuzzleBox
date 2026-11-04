package com.ivandsky.puzzlebox.play

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.ivandsky.puzzlebox.data.repository.KeptSessionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeKeptSessionRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Leaving the play route (ADR-0010): back leaves the play to the picker and keeps it, a
 * new entry over a finished play picks, and a route that leaves composition while the
 * activity stays resumed (a tab switch) keeps the live session before the app can stop.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayRouteExitTest : PlayRouteHarness() {

    @Test
    fun `back from a mid-game play leaves it to the picker`() {
        // Zip-shaped game (the original route test's game): no session is published
        // before the first move, so this still-composed test route has nothing to
        // re-adopt after back (production pops the route) and nothing is kept.
        fakes[alpha].publishesAdoptedSession = false
        koinWith()
        var back = 0
        setContent(onBack = { back++ })
        pick(alpha)
        composeRule.onNodeWithText("alpha board 0").assertExists()

        // Device/system back through PlayRoute's BackHandler: the route pops AND the
        // play is left (kept when started, ADR-0010), so the next play entry picks (A14).
        pressBack()
        assertEquals(1, back)
        composeRule.onNodeWithTag("play.picker").assertExists()
        assertEquals(null, store().active.value)
        assertTrue((koin!!.koin.get<KeptSessionRepository>() as FakeKeptSessionRepository).current.isEmpty())
    }

    @Test
    fun `back on a retained entry shows the picker and resumes with the folded clock`() {
        // Device regression: navigation retains the play tab's entry, so its saveable
        // still holds the last move's bytes (clock banked at 0). Back must not let them
        // resurrect the game over the kept session's later clock fold.
        val kept = FakeKeptSessionRepository()
        koinWith(kept = kept)
        setContent()
        pick(alpha)
        composeRule.runOnIdle { fakes[alpha].latest.move() }
        composeRule.runOnIdle { fakes[alpha].latest.advance(18_000L) }

        pressBack()
        composeRule.onNodeWithTag("play.picker").assertExists()
        assertEquals(null, store().active.value)
        assertEquals(18_000L, kept.current[alpha]?.elapsedMs)

        pick(alpha)
        composeRule.onNodeWithText("alpha board 1").assertExists()
        assertEquals(18_000L, fakes[alpha].latest.displayedElapsedMs())
    }

    @Test
    fun `a route disposed mid-play keeps the live session though the app never stopped`() {
        // Tab switch, then the app is killed in the background: ON_STOP reaches only a
        // composed route, so the play must be kept when the route leaves composition.
        val kept = FakeKeptSessionRepository()
        koinWith(kept = kept)
        setContent()
        pick(alpha)
        composeRule.runOnIdle { fakes[alpha].latest.move() }
        composeRule.runOnIdle { fakes[alpha].latest.advance(12_000L) }

        composeRule.runOnIdle { shown = false }
        composeRule.waitForIdle()

        assertEquals(12_000L, kept.current[alpha]?.elapsedMs)
        assertEquals(day, kept.current[alpha]?.day)
        assertEquals(alpha, store().active.value?.gameId) // kept, not left: the tab comes back to it
    }

    @Test
    fun `a new entry over the result's finished play shows the picker, not the win again`() {
        koinWith()
        setContent()
        pick(alpha)
        composeRule.runOnIdle { fakes[alpha].latest.move() }
        composeRule.runOnIdle { fakes[alpha].latest.win() }
        composeRule.waitForIdle()
        assertEquals(1, handedOff.size)

        // The Play tab after a solve: a new play entry while the finished play is still live.
        composeRule.runOnIdle { entry++ }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("play.picker").assertExists()
        assertEquals(1, handedOff.size)
        assertEquals(null, store().active.value)
    }
}
