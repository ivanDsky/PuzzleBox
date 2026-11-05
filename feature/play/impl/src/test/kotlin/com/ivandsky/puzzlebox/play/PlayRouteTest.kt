package com.ivandsky.puzzlebox.play

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeKeptSessionRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Entering the play route: the picker is the fresh-entry state (A14), picking starts
 * that game in place through its [com.ivandsky.puzzlebox.gameui.GamePlayUi], and a
 * nav-carried [SessionBlob] restores through the named game.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayRouteTest : PlayRouteHarness() {

    @Test
    fun `fresh entry shows the picker with every registered game`() {
        koinWith()
        setContent()
        composeRule.onNodeWithTag("play.picker").assertExists()
        composeRule.onNodeWithText("Pick a game").assertExists()
        composeRule.onNodeWithTag("play.picker_alpha").assertExists()
        composeRule.onNodeWithTag("play.picker_beta").assertExists()
        composeRule.onNodeWithText("Alpha").assertExists()
        composeRule.onNodeWithText("Beta").assertExists()
    }

    @Test
    fun `the picker shows each game's today status and action and a solve flips its row live`() {
        val completions = FakeCompletionRepository()
        val kept = FakeKeptSessionRepository(listOf(keptSession(beta, moves = 2, elapsedMs = 42_000L)))
        koinWith(completions = completions, kept = kept)
        setContent()
        row("status", "alpha").assertTextEquals("Not started")
        row("action", "alpha").assertTextEquals("Play")
        row("status", "beta").assertTextEquals("In progress · 0:42")
        row("action", "beta").assertTextEquals("Continue")

        runBlocking { completions.onCompleted(CompletionRecord(TestGames.Ref(alpha, day, 7), 36_000L, 0, RecordStats(1, "{}"))) }
        composeRule.waitForIdle()

        row("status", "alpha").assertTextEquals("Solved · 0:36 ✓")
        row("action", "alpha").assertTextEquals("Replay")
        row("status", "beta").assertTextEquals("In progress · 0:42")
    }

    // Rows are clickable (merged semantics): the status and action lines are child nodes.
    private fun row(part: String, game: String) =
        composeRule.onNodeWithTag("play.picker_${part}_$game", useUnmergedTree = true)

    @Test
    fun `picking an in-progress game resumes its kept board without a fetch`() {
        val daily = freshDaily()
        koinWith(daily, kept = FakeKeptSessionRepository(listOf(keptSession(beta, moves = 3, elapsedMs = 7_000L))))
        setContent()
        pick(beta)

        composeRule.onNodeWithText("beta board 3").assertExists()
        assertEquals(7_000L, fakes[beta].latest.displayedElapsedMs())
        assertEquals(0, daily.todayCalls(beta))
    }

    @Test
    fun `picking a game starts its screen in place and loads today`() {
        val daily = freshDaily()
        koinWith(daily)
        setContent()
        pick(alpha)

        composeRule.onNodeWithText("alpha board 0").assertExists()
        composeRule.onNodeWithTag("play.picker").assertDoesNotExist()
        assertEquals(1, daily.todayCalls(alpha))
        assertEquals(TestGames.Ref(alpha, day, 7), fakes[alpha].latest.ref)
    }

    @Test
    fun `an unavailable board retries through the screen`() {
        val daily = freshDaily().apply { setToday(alpha, DailyToday.Unavailable(FetchError.Unavailable("offline"))) }
        koinWith(daily)
        setContent()
        pick(alpha)
        composeRule.onNodeWithTag("play.error").assertExists()

        daily.setToday(alpha, DailyToday.Fresh(TestGames.puzzle(alpha)))
        composeRule.onNodeWithText("Try again").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("alpha board 0").assertExists()
        assertEquals(2, daily.todayCalls(alpha))
    }

    @Test
    fun `a replay blob starts its game and restores the archived session without a fetch`() {
        val daily = freshDaily().apply { setToday(beta, DailyToday.Unavailable(FetchError.Throttled())) }
        koinWith(daily)
        val blob = FakeController.envelope(beta, moves = 3, complete = true, day = day.minusDays(30), number = 12)
        setContent(restoredSession = SessionBlob.encode(blob.game, blob.session))
        composeRule.waitForIdle()

        // Restored COMPLETED session → the win handoff fires into navigation once and
        // the archive was NEVER re-fetched (the blob carried the session).
        composeRule.onNodeWithText("beta board 3").assertExists()
        composeRule.onNodeWithTag("play.picker").assertDoesNotExist()
        assertEquals(1, handedOff.size)
        assertEquals(0, daily.todayCalls(beta))
    }

    @Test
    fun `the route redraws the live controller's moves`() {
        koinWith()
        setContent()
        pick(alpha)
        composeRule.runOnIdle { fakes[alpha].latest.move() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("alpha board 1").assertExists()
        assertTrue(store().active.value?.gameId == alpha)
    }
}
