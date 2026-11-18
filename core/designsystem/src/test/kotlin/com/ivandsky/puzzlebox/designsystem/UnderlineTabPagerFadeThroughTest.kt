package com.ivandsky.puzzlebox.designsystem

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import org.junit.After
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
 * Tab changes fade through, following the finger (ticket 142, option E). Each page is a solid colour on white, so the
 * pixel at the pager's centre says how opaque the page there is: page 0 red, page 1 blue, page 2 green.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class UnderlineTabPagerFadeThroughTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val three = listOf(
        UnderlineTab("New today", "t.new", count = 8),
        UnderlineTab("Friends", "t.friends", count = 5),
        UnderlineTab("Yours", "t.yours"),
    )

    private val colours = listOf(Color.Red, Color.Blue, Color.Green)
    private val composed = mutableSetOf<Int>()
    private val drawn = mutableSetOf<Int>()
    private val asked = mutableStateOf<Int?>(null)
    private val settledPages = mutableListOf<Int>()

    @After
    fun resetScale() = animatorScale(1f)

    private fun animatorScale(scale: Float) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    @Composable
    private fun Page(page: Int) {
        DisposableEffect(page) {
            composed += page
            onDispose {}
        }
        Box(
            Modifier.fillMaxSize().testTag("page.$page").drawBehind {
                drawn += page
                drawRect(colours[page])
            },
        )
    }

    private fun show(initialPage: Int = 0, hostDriven: Boolean = false) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Box(Modifier.size(width = 360.dp, height = 400.dp).background(Color.White)) {
                    UnderlineTabPager(
                        tabs = three,
                        onReselect = {},
                        initialPage = initialPage,
                        selectedPage = if (hostDriven) asked.value else null,
                        onPageSettled = { settledPages += it },
                    ) { Page(it) }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(100)
        composed.clear()
        drawn.clear()
    }

    private fun advance(ms: Long) = composeRule.mainClock.advanceTimeBy(ms)

    /** Runs the clock frame by frame for [ms], so a page that exists for one frame is counted. */
    private fun run(ms: Long) = repeat((ms / 16).toInt()) {
        composeRule.mainClock.advanceTimeByFrame()
        // The headless renderer draws a frame only when it is read, so each one is read to see what it drew.
        composeRule.onNodeWithTag("underline.pager").captureToImage()
    }

    private fun pagerWidth() = composeRule.onNodeWithTag("underline.pager").getUnclippedBoundsInRoot().width.value

    private fun tabWidth() = composeRule.onNodeWithTag("underline.tabs").getUnclippedBoundsInRoot().width.value / 3f

    private fun indicatorX() = composeRule.onNodeWithTag("underline.tabs.indicator").getUnclippedBoundsInRoot().left.value

    private fun pageLeft(page: Int) = composeRule.onNodeWithTag("page.$page").getUnclippedBoundsInRoot().left.value

    private fun exists(page: Int) = runCatching { composeRule.onNodeWithTag("page.$page").assertExists() }.isSuccess

    private var fingerDown = false
    private var heldFraction = 0f

    /** Holds a finger [fraction] of a page to the left in all, and returns how far the pager really is (the bar says). */
    private fun hold(fraction: Float): Float {
        val distance = pagerWidth() * (fraction - heldFraction) * composeRule.density.density
        val first = !fingerDown
        composeRule.onNodeWithTag("underline.pager").performTouchInput {
            if (first) down(center)
            moveBy(Offset(-distance, 0f), delayMillis = 400)
        }
        fingerDown = true
        heldFraction = fraction
        composeRule.mainClock.advanceTimeByFrame()
        return indicatorX() / tabWidth()
    }

    private fun release() {
        composeRule.onNodeWithTag("underline.pager").performTouchInput {
            advanceEventTime(600)
            up()
        }
        fingerDown = false
        heldFraction = 0f
    }

    /**
     * How opaque [page] is at the pager's centre. Over white, red at alpha a is (1, 1-a, 1-a), blue is (1-a, 1-a, 1) and
     * green is (1-a, 1, 1-a), so one channel difference names each page whatever else is under it.
     */
    private fun alphaOf(page: Int): Float {
        val map = composeRule.onNodeWithTag("underline.pager").captureToImage().toPixelMap()
        val pixel = map[map.width / 2, map.height / 2]
        val alpha = when (page) {
            0 -> pixel.red - pixel.blue
            1 -> pixel.blue - pixel.red
            else -> pixel.green - pixel.red
        }
        return alpha.coerceAtLeast(0f)
    }

    private fun assertAtRest(page: Int) {
        composeRule.waitForIdle()
        (0..2).filter { it != page }.forEach { assertFalse("page $it is still in the tree", exists(it)) }
        assertTrue("page $page is the one in the tree", exists(page))
        assertEquals("page $page is fully opaque", 1f, alphaOf(page), 0.02f)
        assertEquals(page * tabWidth(), indicatorX(), 1f)
    }

    @Test
    fun `the outgoing page fades with the finger and is gone at half a page`() {
        show()

        val quarter = hold(0.25f)
        assertEquals(1f - 2f * quarter, alphaOf(0), 0.06f)
        assertTrue("a quarter in, the page is half gone", alphaOf(0) in 0.35f..0.65f)

        val half = hold(0.56f) // the finger is still down, a little past half a page
        assertTrue("the finger is at half a page or more ($half)", half >= 0.5f)
        assertEquals("the outgoing page is gone", 0f, alphaOf(0), 0.02f)
        assertEquals("and the incoming one has only begun", 2f * half - 1f, alphaOf(1), 0.06f)
    }

    @Test
    fun `the incoming page is invisible before half a page and partly visible at three quarters`() {
        show()

        val before = hold(0.4f)
        assertTrue("the finger is before half a page ($before)", before < 0.47f)
        assertEquals("the incoming page is invisible", 0f, alphaOf(1), 0.05f)

        val late = hold(0.75f)
        assertTrue("the finger is at about three quarters ($late)", late in 0.65f..0.8f)
        assertEquals(1f - 2f * (1f - late), alphaOf(1), 0.06f)
        assertTrue("three quarters in, the incoming page is partly visible", alphaOf(1) in 0.3f..0.7f)
        assertEquals("the outgoing page is long gone", 0f, alphaOf(0), 0.02f)
    }

    @Test
    fun `the outgoing page follows the finger a sixth of the way`() {
        show()
        val width = pagerWidth()

        val at = hold(0.25f)

        assertEquals("a sixth of the finger's travel", -at * width / 6f, pageLeft(0), 1.5f)
    }

    @Test
    fun `the incoming page starts a twelfth of the width to the side it comes from and settles into place`() {
        show()
        val width = pagerWidth()

        val at = hold(0.6f)
        assertTrue("the finger is past half a page ($at)", at >= 0.5f)
        assertEquals("it waits a sixth of what is left of the finger's travel to the right", (1f - at) * width / 6f, pageLeft(1), 2f)

        release()
        run(700)
        assertAtRest(1)
        assertEquals(0f, pageLeft(1), 0.5f)
    }

    @Test
    fun `after a lift past half the page settles on the next tab on the same clock`() {
        show()

        hold(0.6f)
        release()
        run(120)
        assertTrue("the page is mid-settle after 120ms", indicatorX() / tabWidth() < 0.99f)
        run(400)

        assertAtRest(1)
        composeRule.onNodeWithTag("t.friends").assertIsSelected()
    }

    @Test
    fun `a swipe under half settles back to the first tab at full alpha`() {
        show()

        hold(0.3f)
        release()
        run(700)

        assertAtRest(0)
        composeRule.onNodeWithTag("t.new").assertIsSelected()
    }

    @Test
    fun `a tap from the first tab to the third never composes or draws the one between`() {
        show()

        composeRule.onNodeWithTag("t.yours").performClick()
        run(600)

        assertFalse("Friends was composed: $composed", 1 in composed)
        assertFalse("Friends was drawn: $drawn", 1 in drawn)
        assertTrue("Yours was drawn", 2 in drawn)
        assertAtRest(2)
        composeRule.onNodeWithTag("t.yours").assertIsSelected()
    }

    @Test
    fun `a tap fades the old page out first and the new one in after, never both at once`() {
        show()
        val seen = mutableListOf<Pair<Float, Float>>()

        composeRule.onNodeWithTag("t.friends").performClick()
        repeat(24) {
            composeRule.mainClock.advanceTimeByFrame()
            val old = if (exists(0)) alphaOf(0) else 0f
            val new = if (exists(1)) alphaOf(1) else 0f
            seen += old to new
        }

        assertTrue("the old page fades out ($seen)", seen.any { (old, _) -> old in 0.1f..0.9f })
        assertTrue("the new page fades in ($seen)", seen.any { (_, new) -> new in 0.1f..0.9f })
        assertTrue("never both visible ($seen)", seen.none { (old, new) -> old > 0.05f && new > 0.05f })
        assertAtRest(1)
    }

    @Test
    fun `a host that moves the page fades through the same way and skips the tab between`() {
        show(hostDriven = true)

        composeRule.runOnUiThread { asked.value = 2 }
        run(600)

        assertFalse("Friends was composed: $composed", 1 in composed)
        assertAtRest(2)
        assertEquals("the host hears the page it arrived on", listOf(2), settledPages.distinct())
    }

    @Test
    fun `at rest after a swipe only the selected page is in the tree and it is fully opaque`() {
        show()

        hold(0.7f)
        release()
        run(700)

        assertAtRest(1)
    }

    @Test
    fun `three quick taps rest on the last tab tapped at full alpha`() {
        show()

        composeRule.onNodeWithTag("t.friends").performClick()
        run(32)
        composeRule.onNodeWithTag("t.yours").performClick()
        run(32)
        composeRule.onNodeWithTag("t.friends").performClick()
        run(900)

        assertAtRest(1)
        composeRule.onNodeWithTag("t.friends").assertIsSelected()
    }

    @Test
    fun `taps back and forth across the jump rest on the last tab tapped`() {
        show()

        composeRule.onNodeWithTag("t.yours").performClick()
        run(150) // past the jump: Yours is fading in
        composeRule.onNodeWithTag("t.new").performClick()
        run(900)

        assertAtRest(0)
        composeRule.onNodeWithTag("t.new").assertIsSelected()
    }

    @Test
    fun `a swipe started during a tap's fade rests on a single tab at full alpha`() {
        show()

        composeRule.onNodeWithTag("t.yours").performClick()
        run(48)
        val distance = pagerWidth() * composeRule.density.density
        composeRule.onNodeWithTag("underline.pager").performTouchInput {
            down(center)
            moveBy(Offset(-distance, 0f), delayMillis = 100)
            up()
        }
        run(900)

        composeRule.waitForIdle()
        val resting = (0..2).filter { exists(it) }
        assertEquals("a single page rests: $resting", 1, resting.size)
        assertEquals(1f, alphaOf(resting.single()), 0.02f)
        assertEquals(resting.single() * tabWidth(), indicatorX(), 1f)
    }
}
