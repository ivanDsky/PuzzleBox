package com.ivandsky.puzzlebox.gameui.replay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.PuzzleIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * F10: the hint badge sizes itself from the cell it sits in: the disc is 0.18 of the cell width, the glyph 0.7 of the
 * disc. Measured at a phone cell and a tablet cell, so a fixed size cannot pass.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "xxhdpi")
class ReplayHintBadgeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var secondary = Color.Unspecified
    private var onSecondary = Color.Unspecified

    /** A null [cell] draws the bare reference glyph instead of a badge. One `setContent` per test, so one state drives both. */
    private var cell by mutableStateOf<Dp?>(null)

    private fun setUp() {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                secondary = MaterialTheme.colorScheme.secondary
                onSecondary = MaterialTheme.colorScheme.onSecondary
                val size = cell
                if (size == null) {
                    Box(Modifier.size(REFERENCE_BOX).testTag(CELL)) {
                        Icon(PuzzleIcons.Hint, contentDescription = null, modifier = Modifier.size(REFERENCE_BOX), tint = Color.Black)
                    }
                } else {
                    Box(Modifier.size(size).testTag(CELL)) {
                        ReplayHintBadge(Modifier.align(Alignment.TopEnd).testTag(BADGE))
                    }
                }
            }
        }
    }

    private fun showBadge(size: Dp) {
        cell = size
        setUp()
    }

    private fun disc() = composeRule.onNodeWithTag(BADGE).getUnclippedBoundsInRoot()

    private fun assertDiscIsFractionOf(size: Dp) {
        showBadge(size)

        assertEquals("disc width at a ${size.value} dp cell", size.value * 0.18f, disc().width.value, 0.3f)
        assertEquals("the disc is round", disc().width.value, disc().height.value, 0.01f)
    }

    @Test
    fun `the disc is 0 18 of a phone cell`() = assertDiscIsFractionOf(PHONE_CELL)

    @Test
    fun `the disc is 0 18 of a tablet cell`() = assertDiscIsFractionOf(TABLET_CELL)

    @Test
    fun `the badge sits in the top end corner of the cell`() {
        showBadge(TABLET_CELL)

        val cell = composeRule.onNodeWithTag(CELL).getUnclippedBoundsInRoot()
        assertEquals(cell.right.value, disc().right.value, 0.01f)
        assertEquals(cell.top.value, disc().top.value, 0.01f)
    }

    @Test
    fun `the glyph is 0 7 of the disc at a phone and a tablet cell`() {
        val inkShare = bareGlyphInkShare()

        for (size in listOf(PHONE_CELL, TABLET_CELL)) {
            cell = size
            composeRule.waitForIdle()
            val density = composeRule.density.density
            val expectedPx = size.value * 0.18f * 0.7f * inkShare * density
            val ink = inkWidthPx(composeRule.onNodeWithTag(BADGE).captureToImage().toPixelMap(), insideDisc = true) { it.distanceTo(onSecondary) < it.distanceTo(secondary) }
            assertEquals("glyph ink at a ${size.value} dp cell", expectedPx, ink.toFloat(), expectedPx * 0.12f + 1f)
        }
    }

    /** The share of its box's width the hint glyph's ink spans, from the glyph drawn large and bare. */
    private fun bareGlyphInkShare(): Float {
        cell = null
        setUp()
        val pixels = composeRule.onNodeWithTag(CELL).captureToImage().toPixelMap()
        val share = inkWidthPx(pixels) { it.red < 0.5f } / (REFERENCE_BOX.value * composeRule.density.density)
        assertTrue("the bare glyph drew something: $share", share > 0.1f)
        return share
    }

    /** The widest span of opaque pixels satisfying [isInk]. */
    private fun inkWidthPx(pixels: androidx.compose.ui.graphics.PixelMap, insideDisc: Boolean = false, isInk: (Color) -> Boolean): Int {
        var first = Int.MAX_VALUE
        var last = -1
        for (x in 0 until pixels.width) for (y in 0 until pixels.height) {
            val c = pixels[x, y]
            val radius = pixels.width / 2f
            val inside = !insideDisc || (x + 0.5f - radius).let { dx -> (y + 0.5f - radius).let { dy -> dx * dx + dy * dy < (radius * 0.9f) * (radius * 0.9f) } }
            if (inside && c.alpha > 0.99f && isInk(c)) {
                first = minOf(first, x)
                last = maxOf(last, x)
            }
        }
        return if (last < 0) 0 else last - first + 1
    }

    private fun Color.distanceTo(other: Color): Float =
        (red - other.red) * (red - other.red) + (green - other.green) * (green - other.green) + (blue - other.blue) * (blue - other.blue)

    private companion object {
        const val CELL = "cell"
        const val BADGE = "badge"
        val PHONE_CELL = 40.dp
        val TABLET_CELL = 96.dp
        val REFERENCE_BOX = 200.dp
    }
}
