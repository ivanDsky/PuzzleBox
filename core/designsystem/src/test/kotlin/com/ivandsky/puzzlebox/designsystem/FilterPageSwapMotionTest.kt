package com.ivandsky.puzzlebox.designsystem

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The swap's fade (epic picker-filters, ticket 11): the old content out over 120 ms and the page in over 240 ms on the test clock, both over the
 * reduced duration under reduced motion, neither moving.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class FilterPageSwapMotionTest {

    @get:Rule
    val composeRule = createComposeRule()

    @After
    fun resetScale() = animatorScale(1f)

    private fun animatorScale(scale: Float) {
        Settings.Global.putFloat(ApplicationProvider.getApplicationContext<Context>().contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    private fun advance(ms: Long) = composeRule.mainClock.advanceTimeBy(ms)

    private fun node(tag: String) = composeRule.onNodeWithTag(tag)

    private fun count(tag: String) = composeRule.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    private val swapPage = mutableStateOf<String?>(null)

    /** Two 40dp black squares, the content's at the left and the page's at the right of equal 100dp frames, on white. */
    @Composable
    private fun Swatches() {
        Box(Modifier.background(Color.White)) {
            FilterPageSwap(page = swapPage.value, onBack = {}, pageContent = { Square("to", Alignment.TopEnd) }) { Square("from", Alignment.TopStart) }
        }
    }

    @Composable
    private fun Square(tag: String, at: Alignment) {
        Box(Modifier.size(width = 100.dp, height = 40.dp)) { Box(Modifier.align(at).size(40.dp).testTag(tag).background(Color.Black)) }
    }

    private fun shade(tag: String): Float? {
        if (count(tag) == 0) return null
        val image = node(tag).captureToImage()
        return image.toPixelMap()[image.width / 2, image.height / 2].luminance()
    }

    /** The two squares' luminance 60 ms, 130 ms and 260 ms after the swap starts; null is a square that has left the tree. */
    private fun swapSamples(reduced: Boolean): List<Pair<Float?, Float?>> {
        animatorScale(if (reduced) 0f else 1f)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { PuzzleBoxTheme(darkTheme = false) { Swatches() } }
        composeRule.mainClock.advanceTimeByFrame()
        val fromLeft = node("from").getUnclippedBoundsInRoot().left
        composeRule.runOnUiThread { swapPage.value = "page" }
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeByFrame()
        var elapsed = 0L
        return listOf(60L, 130L, 260L).map { at ->
            advance(at - elapsed)
            elapsed = at
            if (count("from") > 0) assertEquals("the old content does not slide", fromLeft, node("from").getUnclippedBoundsInRoot().left)
            shade("from") to shade("to")
        }
    }

    @Test
    fun `the swap fades the old out over 120 ms and the new in over 240 ms without moving either`() {
        val (at60, at130, at260) = swapSamples(reduced = false)

        assertTrue("old part way at 60 ms: $at60", at60.first!! in 0.3f..0.9f)
        assertTrue("new part way at 60 ms: $at60", at60.second!! in 0.1f..0.5f)
        assertTrue("old gone by 130 ms: $at130", (at130.first ?: 1f) > 0.95f)
        assertTrue("new not yet whole at 130 ms: $at130", at130.second!! > 0.003f)
        assertNull("old left the tree", at260.first)
        assertTrue("new whole at 260 ms: $at260", at260.second!! < 0.002f)
    }

    @Test
    fun `under reduced motion both fade over the reduced duration`() {
        val (at60, at130, _) = swapSamples(reduced = true)

        assertTrue("old part way at 60 ms: $at60", at60.first!! in 0.3f..0.9f)
        assertTrue("new nearly whole at 60 ms, sooner than the 240 ms fade: $at60", at60.second!! < 0.08f)
        assertNull("old left the tree by 130 ms", at130.first)
        assertTrue("new whole at 130 ms: $at130", at130.second!! < 0.002f)
    }
}
