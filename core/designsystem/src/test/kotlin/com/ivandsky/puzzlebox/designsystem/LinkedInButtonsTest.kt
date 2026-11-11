package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The LinkedIn-branded buttons: labelled, at least 48 dp tall in both themes, and they report their taps. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class LinkedInButtonsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `connect reports a tap and keeps its label`() {
        var taps = 0
        composeRule.setContent {
            PuzzleBoxTheme { LinkedInConnectButton("Connect LinkedIn", onClick = { taps++ }, modifier = Modifier.fillMaxWidth()) }
        }

        composeRule.onNodeWithText("Connect LinkedIn").performClick()

        assertEquals(1, taps)
    }

    @Test
    fun `resume reports a tap and a disabled one does not`() {
        var taps = 0
        composeRule.setContent {
            PuzzleBoxTheme {
                Column {
                    LinkedInResumeButton("Resume", onClick = { taps++ })
                    LinkedInResumeButton("Off", onClick = { taps += 10 }, enabled = false)
                }
            }
        }

        composeRule.onNodeWithText("Resume").performClick()
        composeRule.onNodeWithText("Off").assertIsNotEnabled()

        assertEquals(1, taps)
    }

    @Test
    fun `both buttons are at least 48 dp tall in light and dark`() {
        composeRule.setContent {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                Column {
                    PuzzleBoxTheme(darkTheme = false) {
                        LinkedInConnectButton("Connect light", onClick = {})
                        LinkedInResumeButton("Resume light", onClick = {})
                    }
                    PuzzleBoxTheme(darkTheme = true) {
                        LinkedInConnectButton("Connect dark", onClick = {})
                        LinkedInResumeButton("Resume dark", onClick = {})
                    }
                }
            }
        }

        for (label in listOf("Connect light", "Resume light", "Connect dark", "Resume dark")) {
            composeRule.onNodeWithText(label).assertHeightIsAtLeast(48.dp)
        }
    }
}
