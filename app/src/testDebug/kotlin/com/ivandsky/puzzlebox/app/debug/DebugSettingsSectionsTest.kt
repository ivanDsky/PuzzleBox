package com.ivandsky.puzzlebox.app.debug

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.app.debug.flags.DebugReleaseFlags
import com.ivandsky.puzzlebox.app.debug.flags.ReleaseFlagsPanel
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
class DebugSettingsSectionsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Before
    fun clearOverrides() {
        RuntimeEnvironment.getApplication().getSharedPreferences("release_flags", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

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

    /** Characterization: every flag gets a row that names it and its release default; a forced one says so. */
    @Test
    fun `every release flag row shows its name and release default`() {
        assertTrue("no release flag: move this test to a test-only flag", ReleaseFlag.entries.isNotEmpty())
        val flags = DebugReleaseFlags(RuntimeEnvironment.getApplication())
        val forced = ReleaseFlag.entries.first()
        flags.setOverride(forced, !forced.releaseDefault)
        composeRule.setContent { PuzzleBoxTheme { ReleaseFlagsPanel(flags = flags) } }

        ReleaseFlag.entries.forEach {
            val release = if (it.releaseDefault) "on" else "off"
            val text = if (it == forced) "${it.name} · release: $release · forced" else "${it.name} · release: $release"
            composeRule.onNodeWithText(text).assertExists()
        }
    }
}
