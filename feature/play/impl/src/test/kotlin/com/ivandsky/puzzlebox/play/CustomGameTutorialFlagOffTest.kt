package com.ivandsky.puzzlebox.play

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** PlayFeel off: a friend's puzzle with unseen lessons is today's board, with no gate and no How to play. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CustomGameTutorialFlagOffTest : GameTutorialFixture(custom = true, playFeel = false) {

    @Test
    fun `a friend puzzle with unseen lessons shows its board without a gate or how to play`() {
        fakes[alpha].teach(2)
        val prefs = FakeUserPreferencesRepository()
        start(prefs)

        composeRule.onNodeWithText("alpha board 0").assertExists()
        composeRule.onNodeWithTag("tutorial.host").assertDoesNotExist()
        composeRule.onNodeWithTag("play.how_to_play").assertDoesNotExist()
        assertTrue(prefs.seen.isEmpty())
    }
}
