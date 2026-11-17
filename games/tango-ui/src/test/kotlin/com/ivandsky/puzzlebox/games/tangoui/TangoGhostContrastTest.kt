package com.ivandsky.puzzlebox.games.tangoui

import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Tutorial guide ghosts must stand clear of their empty square (#1186). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-420dpi")
class TangoGhostContrastTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun assertGhostClear(dark: Boolean, minContrast: Float) {
        composeRule.setContent { TangoLessonOneSample(dark) }
        composeRule.waitForIdle()
        val bounds = composeRule.onNodeWithContentDescription("Row 2, column 3", substring = true)
            .fetchSemanticsNode().boundsInRoot
        val pixels = composeRule.onRoot().captureToImage().toPixelMap()
        val centre = pixels[bounds.center.x.toInt(), bounds.center.y.toInt()]
        // Just inside the corner: past the gridline, outside the inset outline and the glyph.
        val empty = pixels[(bounds.left + 3f).toInt(), (bounds.top + 3f).toInt()]
        val contrast = abs(centre.luminance() - empty.luminance())
        assertTrue("dark=$dark ghost contrast $contrast too faint ($centre on $empty)", contrast > minContrast)
    }

    /** Bug it catches: the ghost sun drifting back to the faint 0.4 look that read as a disabled square. */
    @Test
    fun `a light ghost sun stands clearer of its empty square than the old 0_4 ghost`() =
        assertGhostClear(dark = false, minContrast = OLD_LIGHT_CONTRAST)

    @Test
    fun `a dark ghost sun stands clearer of its empty square than the old 0_4 ghost`() =
        assertGhostClear(dark = true, minContrast = OLD_DARK_CONTRAST)

    private companion object {
        /** Luminance gap of the ghost sun centre over its empty square, measured at the old 0.4 alpha (dark: derived by blending from the 0.62 render). */
        const val OLD_LIGHT_CONTRAST = 0.369f
        const val OLD_DARK_CONTRAST = 0.067f
    }
}
