package com.ivandsky.puzzlebox.play

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The first-play tutorial while its seen-read is still pending: the board composes at once,
 * and a late read never reopens a tutorial the player already opened or closed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GameTutorialPendingReadTest : GameTutorialFixture() {

    @Test
    fun `the board composes while the tutorial-seen read is pending, and a first play's lessons follow it`() {
        fakes[alpha].teach(2)
        val prefs = SlowTutorialUserPreferencesRepository(FakeUserPreferencesRepository())
        start(prefs)

        composeRule.onNodeWithText("alpha board 0").assertExists()
        composeRule.onNodeWithTag("tutorial.host").assertDoesNotExist()

        composeRule.runOnIdle { prefs.release.complete(Unit) }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("tutorial.host").assertExists()
        composeRule.onNodeWithContentDescription("Step 1 of 2").assertExists()
    }

    @Test
    fun `a skip during the pending read holds when the read lands unseen`() {
        fakes[alpha].teach(2)
        val prefs = SlowTutorialUserPreferencesRepository(FakeUserPreferencesRepository())
        start(prefs)

        click("play.how_to_play")
        click("tutorial.skip")
        composeRule.runOnIdle { prefs.release.complete(Unit) }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("tutorial.host").assertDoesNotExist()
        composeRule.onNodeWithText("alpha board 0").assertExists()
        assertEquals(setOf(alpha), prefs.inner.seen)
    }

    /** Its tutorial-seen read snapshots at subscribe time and lands only once [release] completes, as a DataStore read on IO does. */
    private class SlowTutorialUserPreferencesRepository(val inner: FakeUserPreferencesRepository) :
        UserPreferencesRepository by inner {
        val release = CompletableDeferred<Unit>()

        override fun tutorialSeen(game: GameId): Flow<Boolean> = flow {
            val snapshot = inner.tutorialSeen(game).first()
            release.await()
            emit(snapshot)
        }
    }
}
