package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
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
 * A pane beside the pager changes with it (ticket 142): on the same link it is as faded as the list page next to it, at
 * every point of a finger's drag and of a tap, and only the tab a change is on or between is in its tree.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w600dp-h400dp-420dpi")
class TabFadePaneTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val three = listOf(
        UnderlineTab("New today", "t.new"),
        UnderlineTab("Friends", "t.friends"),
        UnderlineTab("Yours", "t.yours"),
    )
    private val colours = listOf(Color.Red, Color.Blue, Color.Green)
    private val paneComposed = mutableSetOf<Int>()

    @Composable
    private fun Solid(tag: String, page: Int) {
        Box(Modifier.fillMaxSize().testTag("$tag.$page").background(colours[page]))
    }

    /** The pager and the pane side by side on one link, the pane's page the colour of the list page next to it. */
    private fun show() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                val link = remember { TabFadeLink(0) }
                Row(Modifier.size(width = 600.dp, height = 400.dp).background(Color.White)) {
                    UnderlineTabPager(three, onReselect = {}, modifier = Modifier.width(300.dp), link = link) { Solid("list", it) }
                    TabFadePane(link, Modifier.width(300.dp).fillMaxSize().testTag("pane")) { page ->
                        DisposableEffect(page) {
                            paneComposed += page
                            onDispose {}
                        }
                        Solid("pane", page)
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(100)
        paneComposed.clear()
    }

    /** How opaque page [page] is at the centre of [tag]: red, blue and green over white each differ in one channel. */
    private fun alphaAt(tag: String, page: Int): Float {
        val map = composeRule.onNodeWithTag(tag).captureToImage().toPixelMap()
        val pixel = map[map.width / 2, map.height / 2]
        return when (page) {
            0 -> pixel.red - pixel.blue
            1 -> pixel.blue - pixel.red
            else -> pixel.green - pixel.red
        }.coerceAtLeast(0f)
    }

    private fun listAlpha(page: Int) = alphaAt("underline.pager", page)

    private fun paneAlpha(page: Int) = alphaAt("pane", page)

    private fun exists(tag: String) = runCatching { composeRule.onNodeWithTag(tag).assertExists() }.isSuccess

    private var held = 0f

    private fun hold(fraction: Float) {
        val width = composeRule.onNodeWithTag("underline.pager").getUnclippedBoundsInRoot().width.value
        val distance = width * (fraction - held) * composeRule.density.density
        val first = held == 0f
        composeRule.onNodeWithTag("underline.pager").performTouchInput {
            if (first) down(center)
            moveBy(Offset(-distance, 0f), delayMillis = 400)
        }
        held = fraction
        composeRule.mainClock.advanceTimeByFrame()
    }

    @Test
    fun `the pane is as faded as the list through a swipe`() {
        show()

        for (fraction in listOf(0.25f, 0.45f, 0.62f, 0.75f)) {
            hold(fraction)
            val list = listAlpha(0) + listAlpha(1)
            val pane = paneAlpha(0) + paneAlpha(1)
            assertEquals("pane and list at $fraction of a swipe", list, pane, 0.04f)
        }
        assertTrue("it fades in over the second half, not at all before it", paneAlpha(1) > 0.2f || listAlpha(1) > 0.2f)
    }

    @Test
    fun `the pane never shows two tabs at once through a swipe`() {
        show()

        hold(0.45f)
        assertEquals("the pane's next tab has not begun", 0f, paneAlpha(1), 0.04f)
        hold(0.7f)
        assertEquals("the pane's first tab is gone", 0f, paneAlpha(0), 0.04f)
    }

    @Test
    fun `the pane fades through a tap with the list and never composes the tab between`() {
        show()

        composeRule.onNodeWithTag("t.yours").performClick()
        var sawOut = false
        var sawIn = false
        repeat(30) {
            composeRule.mainClock.advanceTimeByFrame()
            val list = listAlpha(0) + listAlpha(2)
            val pane = paneAlpha(0) + paneAlpha(2)
            assertEquals("pane and list agree", list, pane, 0.04f)
            if (pane in 0.1f..0.9f && paneAlpha(0) > 0f) sawOut = true
            if (pane in 0.1f..0.9f && paneAlpha(2) > 0f) sawIn = true
        }

        assertTrue("the pane faded out", sawOut)
        assertTrue("then in", sawIn)
        assertFalse("the pane never composed the tab between: $paneComposed", 1 in paneComposed)
        assertEquals(1f, paneAlpha(2), 0.02f)
    }

    @Test
    fun `at rest only the selected tab's pane is in the tree`() {
        show()
        assertTrue(exists("pane.0"))
        assertFalse(exists("pane.1"))
        assertFalse(exists("pane.2"))

        composeRule.onNodeWithTag("t.friends").performClick()
        repeat(30) {
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.onNodeWithTag("underline.pager").captureToImage()
        }

        assertFalse(exists("pane.0"))
        assertTrue(exists("pane.1"))
        assertFalse(exists("pane.2"))
    }
}
