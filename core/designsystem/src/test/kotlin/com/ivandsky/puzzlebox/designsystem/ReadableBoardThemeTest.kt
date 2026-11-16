package com.ivandsky.puzzlebox.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// The theme's one readableBoard Boolean picks the board colour instance as well as the geometry
// (nav-rethink B3); with it off every theme keeps today's tokens.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReadableBoardThemeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `a theme with no request provides the legacy instance`() {
        var seen: BoardColors? = null
        composeRule.setContent { PuzzleBoxTheme(darkTheme = true) { seen = LocalBoardColors.current } }
        composeRule.waitForIdle()
        assertEquals(DarkBoardTokens, seen)
    }

    @Test
    fun `the request picks the instance of the resolved theme and follows a change`() {
        var dark by mutableStateOf(true)
        var readable by mutableStateOf(true)
        var seen: BoardColors? = null
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = dark, readableBoard = readable) { seen = LocalBoardColors.current }
        }
        composeRule.waitForIdle()
        assertEquals(ReadableDarkBoardTokens, seen)

        composeRule.runOnUiThread { dark = false }
        composeRule.waitForIdle()
        assertEquals(ReadableLightBoardTokens, seen)

        composeRule.runOnUiThread { readable = false }
        composeRule.waitForIdle()
        assertEquals(LightBoardTokens, seen)

        composeRule.runOnUiThread { dark = true }
        composeRule.waitForIdle()
        assertEquals(DarkBoardTokens, seen)
    }
}
