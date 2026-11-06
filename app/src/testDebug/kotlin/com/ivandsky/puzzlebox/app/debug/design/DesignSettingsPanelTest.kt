package com.ivandsky.puzzlebox.app.debug.design

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DesignSettingsPanelTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the play button opens the unlisted 9x9`() {
        val opened = mutableListOf<String>()
        composeRule.setContent { PuzzleBoxTheme { DesignSettingsPanel(openGame = { opened += it }) } }

        composeRule.onNodeWithTag("debug.play.sudoku").performScrollTo().performClick()

        assertEquals(listOf("sudoku"), opened)
    }
}
