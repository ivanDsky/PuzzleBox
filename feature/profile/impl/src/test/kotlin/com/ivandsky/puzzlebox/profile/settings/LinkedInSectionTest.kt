package com.ivandsky.puzzlebox.profile.settings

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Robolectric compose coverage for the Settings LinkedIn block: one button set per state, one report per tap. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class LinkedInSectionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val taps = mutableListOf<String>()

    private fun setContent(state: LinkedInSectionState) {
        composeRule.setContent {
            PuzzleBoxTheme {
                LinkedInSection(
                    state = state,
                    onConnect = { taps += "connect" },
                    onResume = { taps += "resume" },
                    onDisconnect = { taps += "disconnect" },
                )
            }
        }
    }

    @Test
    fun `not connected shows only Connect and tapping it reports connect once`() {
        setContent(LinkedInSectionState.NotConnected)

        composeRule.onNodeWithTag("settings.linkedin").assertExists()
        composeRule.onNodeWithTag("settings.linkedin.resume").assertDoesNotExist()
        composeRule.onNodeWithTag("settings.linkedin.disconnect").assertDoesNotExist()
        composeRule.onNodeWithTag("settings.linkedin.connect").performClick()

        assertEquals(listOf("connect"), taps)
    }

    @Test
    fun `connected shows only Disconnect and tapping it reports disconnect once`() {
        setContent(LinkedInSectionState.Connected)

        composeRule.onNodeWithText("Connected to LinkedIn").assertExists()
        composeRule.onNodeWithTag("settings.linkedin.connect").assertDoesNotExist()
        composeRule.onNodeWithTag("settings.linkedin.resume").assertDoesNotExist()
        composeRule.onNodeWithTag("settings.linkedin.disconnect").performClick()

        assertEquals(listOf("disconnect"), taps)
    }

    @Test
    fun `paused shows the reason Resume and Disconnect and Resume reports resume not connect`() {
        setContent(LinkedInSectionState.Paused(reason = "LinkedIn asked for a check.", needsReconnect = false))

        composeRule.onNodeWithText("LinkedIn asked for a check.").assertExists()
        composeRule.onNodeWithTag("settings.linkedin.connect").assertDoesNotExist()
        composeRule.onNodeWithTag("settings.linkedin.disconnect").assertExists()
        composeRule.onNodeWithTag("settings.linkedin.resume").performClick()

        assertEquals(listOf("resume"), taps)
    }

    @Test
    fun `paused after a rejected session shows Reconnect that reports connect and no Resume`() {
        setContent(LinkedInSectionState.Paused(reason = "LinkedIn signed this phone out.", needsReconnect = true))

        composeRule.onNodeWithText("LinkedIn signed this phone out.").assertExists()
        composeRule.onNodeWithTag("settings.linkedin.resume").assertDoesNotExist()
        composeRule.onNodeWithTag("settings.linkedin.disconnect").assertExists()
        composeRule.onNodeWithText("Reconnect").assertExists()
        composeRule.onNodeWithTag("settings.linkedin.connect").performClick()

        assertEquals(listOf("connect"), taps)
    }
}
