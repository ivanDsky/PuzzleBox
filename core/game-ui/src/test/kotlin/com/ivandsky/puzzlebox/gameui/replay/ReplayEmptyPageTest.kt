package com.ivandsky.puzzlebox.gameui.replay

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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

/** The replay page without a replay: the same bar as the player's page, the no-replay words in its body, and Back. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ReplayEmptyPageTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `it draws the bar, the context and the no-replay words, and Back goes back`() {
        var backs = 0
        composeRule.setContent { PuzzleBoxTheme(darkTheme = false) { ReplayEmptyPage("Queens · #7", onBack = { backs++ }) } }

        composeRule.onNodeWithText(ReplayCopy.TITLE).assertExists()
        composeRule.onNodeWithText("Queens · #7").assertExists()
        composeRule.onNodeWithTag(ReplayTags.NONE).assertExists()
        composeRule.onNodeWithText(ReplayCopy.NO_REPLAY).assertExists()
        composeRule.onNodeWithContentDescription(ReplayCopy.BACK).performClick()
        assertEquals(1, backs)
    }
}
