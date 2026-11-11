package com.ivandsky.puzzlebox.app.debug

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.app.debug.flags.DebugReleaseFlags
import com.ivandsky.puzzlebox.app.debug.flags.ReleaseFlagsPanel
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DebugSettingsSectionsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the debug games row opens the unlisted 9x9`() {
        val opened = mutableListOf<String>()
        composeRule.setContent { PuzzleBoxTheme { DebugGamesSection(openGame = { opened += it }) } }
        composeRule.onNodeWithTag("debug.play.sudoku").performClick()

        assertEquals(listOf("sudoku"), opened)
    }

    @Test
    fun `release flags render inline from the override store`() {
        composeRule.setContent {
            PuzzleBoxTheme { ReleaseFlagsPanel(flags = DebugReleaseFlags(LocalContext.current)) }
        }

        ReleaseFlag.entries.forEach {
            composeRule.onNodeWithTag("debug.flag.${it.name}").assertExists()
        }
    }
}
