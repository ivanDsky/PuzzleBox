package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The page dots as they are drawn (user-check F2): the selected dot is the long accent one, and with a scrub the dots
 * move with it, the long dot handing its length and colour to its neighbour as far as the scrub has gone, so a swipe
 * that commits never makes them jump. Four boards; a dp is a pixel here.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PageDotsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var index by mutableIntStateOf(1)
    private lateinit var scrub: BoardScrub

    // The dots over a flat colour that no dot has, so each dot is a run of other pixels.
    private fun show() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Box(Modifier.background(Behind)) {
                    scrub = rememberBoardScrub(index)
                    PageDots(COUNT, index, Modifier.testTag("dots"), scrub = scrub)
                }
            }
        }
        frame()
    }

    private fun frame() = composeRule.mainClock.advanceTimeByFrame()

    private fun drag(delta: Float) {
        composeRule.runOnUiThread { scrub.dragBy(delta, COUNT) }
        frame()
    }

    private data class Dot(val width: Int, val color: Int)

    private fun dots(): List<Dot> {
        val image = composeRule.onNodeWithTag("dots").captureToImage().asAndroidBitmap()
        val y = image.height / 2
        val dots = mutableListOf<Dot>()
        var start = -1
        for (x in 0..image.width) {
            val drawn = x < image.width && image.getPixel(x, y) != Behind.toArgb()
            if (drawn && start < 0) start = x
            if (!drawn && start >= 0) {
                dots += Dot(x - start, image.getPixel((start + x) / 2, y))
                start = -1
            }
        }
        return dots
    }

    private fun widths() = dots().map { it.width }

    @Test
    fun `the selected dot is the long one, in its own colour`() {
        show()

        val dots = dots()

        assertEquals(listOf(6, 18, 6, 6), dots.map { it.width })
        assertEquals(listOf(dots[0].color, dots[0].color), listOf(dots[2].color, dots[3].color))
        assertNotEquals(dots[0].color, dots[1].color)
    }

    @Test
    fun `the dots follow the finger, the long dot handing its length and colour to the next`() {
        show()
        val (off, on) = dots()

        drag(-0.25f)
        assertEquals(listOf(6, 15, 9, 6), widths())

        drag(-0.25f)
        val halfway = dots()
        assertEquals(listOf(6, 12, 12, 6), halfway.map { it.width })
        assertEquals(halfway[1].color, halfway[2].color)
        assertNotEquals(on.color, halfway[1].color)
        assertNotEquals(off.color, halfway[1].color)
        assertEquals(listOf(off.color, off.color), listOf(halfway[0].color, halfway[3].color))
    }

    @Test
    fun `the dots follow the finger back towards the previous board`() {
        show()

        drag(0.5f)

        assertEquals(listOf(12, 12, 6, 6), widths())
    }

    @Test
    fun `a committed swipe does not jump the dots, they land with the scrub`() {
        show()
        val (off, on) = dots()
        drag(-0.5f)

        // What the screen does on a release: the scrub settles on the next board and the hub's position follows.
        composeRule.runOnUiThread { scrub.release(position = 1, velocity = 0f, fling = 1f, commit = 0.25f) }
        index = 2
        frame()
        frame()

        val after = widths()
        assertEquals(6, after[0])
        assertTrue("left dot $after", after[1] in 10..12)
        assertTrue("next dot $after", after[2] in 12..14)
        assertEquals(6, after[3])

        composeRule.mainClock.advanceTimeBy(LANDED_MS)
        val landed = dots()
        assertEquals(listOf(6, 6, 18, 6), landed.map { it.width })
        assertEquals(listOf(off.color, off.color, on.color, off.color), landed.map { it.color })
    }

    private companion object {
        const val COUNT = 4
        const val LANDED_MS = 2_000L
        val Behind = Color.Yellow
    }
}
