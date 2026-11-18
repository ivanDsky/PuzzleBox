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

// The theme provides the board colour instance of the resolved theme and follows a live change (nav-rethink B3).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BoardColorsThemeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the theme provides the instance of the resolved theme and follows a change`() {
        var dark by mutableStateOf(true)
        var seen: BoardColors? = null
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = dark) { seen = LocalBoardColors.current }
        }
        composeRule.waitForIdle()
        assertEquals(DarkBoardTokens, seen)

        composeRule.runOnUiThread { dark = false }
        composeRule.waitForIdle()
        assertEquals(LightBoardTokens, seen)
    }
}
