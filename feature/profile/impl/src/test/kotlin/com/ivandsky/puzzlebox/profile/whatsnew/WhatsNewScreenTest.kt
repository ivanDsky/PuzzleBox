package com.ivandsky.puzzlebox.profile.whatsnew

import android.app.Application
import androidx.compose.ui.test.junit4.v2.createComposeRule
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

/** The What's new page: its title, an empty list area and the Back icon. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], application = Application::class)
class WhatsNewScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var backClicks = 0

    @Test
    fun `the page says What's new over an empty list and the back icon calls back`() {
        composeRule.setContent { PuzzleBoxTheme { WhatsNewScreen(onBack = { backClicks++ }) } }

        composeRule.onNodeWithTag("whatsNew.list").assertExists()
        composeRule.onNodeWithTag("whatsNew.back").performClick()

        assertEquals(1, backClicks)
        composeRule.onNodeWithText("What's new").assertExists()
    }
}
