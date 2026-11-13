package com.ivandsky.puzzlebox.home

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Golden screenshots of the Home streak card (phone, at the 360 dp floor) and the tablet
 * streak pane with 3 freezes held and one frozen day (ticket 08, A13), light and dark.
 * The freeze count shows by its spoken label, and the card's line keeps one row beside it.
 * Recorded with `-Proborazzi.record=true`, verified with `-Proborazzi.verify=true`; goldens
 * live in `feature/home/impl/snapshots/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class HomeStreakCardGoldenTest : HomeStreakScenes() {
    @Test fun `card with freezes light`() = captureFrozen("home-streak-freeze-phone360", dark = false) { HomeStreakCardFrozenPreview() }
    @Test fun `card with freezes dark`() = captureFrozen("home-streak-freeze-phone360", dark = true) { HomeStreakCardFrozenPreview() }

    @Test
    fun `the card's line stays on one row beside the count`() {
        render(dark = false) { HomeStreakCardFrozenPreview() }
        val results = mutableListOf<TextLayoutResult>()
        val line = composeRule.onNodeWithText("12 days in a row · Best 21").fetchSemanticsNode()
        checkNotNull(line.config[SemanticsActions.GetTextLayoutResult].action).invoke(results)
        assertEquals("streak line wrapped", 1, results.single().lineCount)
        assertFalse("streak line ellipsized", results.single().isLineEllipsized(0))
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
class HomeStreakPaneGoldenTest : HomeStreakScenes() {
    @Test fun `pane with freezes light`() = captureFrozen("home-streak-freeze-tablet1280", dark = false) { HomeStreakPaneFrozenPreview() }
    @Test fun `pane with freezes dark`() = captureFrozen("home-streak-freeze-tablet1280", dark = true) { HomeStreakPaneFrozenPreview() }

    @Test
    fun `without freezes the pane shows no count`() {
        render(dark = false) { HomeStreakPanePreview() }
        composeRule.onNodeWithContentDescription("freeze", substring = true).assertDoesNotExist()
    }
}

abstract class HomeStreakScenes {

    @get:Rule
    val composeRule = createComposeRule()

    protected fun captureFrozen(name: String, dark: Boolean, content: @Composable () -> Unit) {
        render(dark, content)
        composeRule.onNodeWithContentDescription("3 freezes").assertIsDisplayed()
        composeRule.onRoot().captureRoboImage(out(name, dark))
    }

    protected fun render(dark: Boolean, content: @Composable () -> Unit) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            // The previews' PuzzleBoxTheme follows the system theme, so dark scenes set the
            // night uiMode exactly as the preview's uiMode (or a device) would.
            val config = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config, content = content)
        }
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
        composeRule.waitForIdle()
    }

    private fun out(name: String, dark: Boolean): String =
        File(checkNotNull(System.getProperty("user.dir")))
            .resolve("snapshots/$name-${if (dark) "dark" else "light"}.png")
            .canonicalFile
            .also { checkNotNull(it.parentFile).mkdirs() }
            .absolutePath

    private companion object {
        /** Past the pane's entrance slide and any first-frame settle. */
        const val SETTLE_MS = 2_000L
    }
}
