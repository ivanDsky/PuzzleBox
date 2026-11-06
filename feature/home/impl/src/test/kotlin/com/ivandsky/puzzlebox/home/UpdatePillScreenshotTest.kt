package com.ivandsky.puzzlebox.home

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.ivandsky.puzzlebox.designsystem.Spacing
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Golden screenshots of the Update available pill previews (ticket 07, S7/S8), light and
 * dark: the pill alone, the single-pane Home header at the 360 dp phone floor, and the
 * TwoPane list pane on a 1280 dp tablet and at the narrowest TwoPane window (840 dp
 * landscape, a ~353 dp list pane). Each header scene also asserts the S7 fit: "PuzzleBox"
 * and the pill label stay on one line with no overflow, and the pill ends inside its pane.
 * Recorded by default, verified with `-Proborazzi.verify=true`; goldens live in
 * `feature/home/impl/snapshots/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class UpdatePillPhoneScreenshotTest : UpdatePillScenes() {
    @Test fun `pill alone light`() = capture("update-pill", dark = false) { UpdatePillPreview() }
    @Test fun `pill alone dark`() = capture("update-pill", dark = true) { UpdatePillPreview() }

    @Test fun `phone header light`() = captureHeader("home-update-phone360", dark = false, paneFraction = 1f) { HomeUpdatePhonePreview() }
    @Test fun `phone header dark`() = captureHeader("home-update-phone360", dark = true, paneFraction = 1f) { HomeUpdatePhonePreview() }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
class UpdatePillTabletScreenshotTest : UpdatePillScenes() {
    @Test fun `tablet list pane light`() = captureHeader("home-update-tablet1280", dark = false) { HomeUpdateTabletPreview() }
    @Test fun `tablet list pane dark`() = captureHeader("home-update-tablet1280", dark = true) { HomeUpdateTabletPreview() }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w840dp-h600dp-land-mdpi")
class UpdatePillNarrowTabletScreenshotTest : UpdatePillScenes() {
    @Test fun `narrowest two pane light`() = captureHeader("home-update-tablet840", dark = false) { HomeUpdateTabletPreview() }
    @Test fun `narrowest two pane dark`() = captureHeader("home-update-tablet840", dark = true) { HomeUpdateTabletPreview() }
}

abstract class UpdatePillScenes {

    @get:Rule
    val composeRule = createComposeRule()

    protected fun capture(name: String, dark: Boolean, content: @Composable () -> Unit) {
        render(dark, content)
        composeRule.onRoot().captureRoboImage(out(name, dark))
    }

    /** [paneFraction] is the list pane's share of the window: HomeScreen's TwoPane weight, or the whole width. */
    protected fun captureHeader(
        name: String,
        dark: Boolean,
        paneFraction: Float = LIST_PANE_WEIGHT,
        content: @Composable () -> Unit,
    ) {
        render(dark, content)
        assertSingleLine(composeRule.onNodeWithText("PuzzleBox"), "title")
        assertSingleLine(composeRule.onNodeWithText("Update available", useUnmergedTree = true), "pill label")
        val paneRight: Dp = composeRule.onRoot().getBoundsInRoot().right * paneFraction
        val pillRight = composeRule.onNodeWithTag("home.update_pill").getBoundsInRoot().right
        val insetEdge = paneRight - Spacing.lg
        assertTrue("pill ends at $pillRight, past its pane's inset edge $insetEdge", pillRight <= insetEdge + PIXEL_SLACK)
        composeRule.onRoot().captureRoboImage(out(name, dark))
    }

    private fun render(dark: Boolean, content: @Composable () -> Unit) {
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

    private fun assertSingleLine(node: SemanticsNodeInteraction, what: String) {
        val results = mutableListOf<TextLayoutResult>()
        checkNotNull(node.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action).invoke(results)
        val layout = results.single()
        assertEquals("$what wrapped", 1, layout.lineCount)
        assertFalse("$what ellipsized", layout.isLineEllipsized(0))
        assertTrue("$what cut off", layout.getLineRight(0) <= layout.size.width)
    }

    private fun out(name: String, dark: Boolean): String =
        File(checkNotNull(System.getProperty("user.dir")))
            .resolve("snapshots/$name-${if (dark) "dark" else "light"}.png")
            .canonicalFile
            .also { checkNotNull(it.parentFile).mkdirs() }
            .absolutePath

    private companion object {
        /** HomeScreen's TwoPane list-pane weight. */
        const val LIST_PANE_WEIGHT = 0.42f

        /** Past Home's staggered entrance (four items, fade-only) and any first-frame settle. */
        const val SETTLE_MS = 2_000L

        /** The weighted pane split lands on whole pixels, so its edge can round past the dp math by under 1 dp. */
        val PIXEL_SLACK = 1.dp
    }
}
