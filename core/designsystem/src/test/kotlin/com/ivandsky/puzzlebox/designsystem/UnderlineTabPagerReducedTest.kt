package com.ivandsky.puzzlebox.designsystem

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.MotionDurationScale
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
 * The pager under reduced motion (ticket 142): animator scale 0, which is also what makes Compose's animations land at
 * once, so the rule's effect context carries the same zero scale the app's windows do. A swipe is the same fade through
 * in place with no shift, a tap lands in a frame, and the bar jumps.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class UnderlineTabPagerReducedTest {

    private object ZeroScale : MotionDurationScale {
        override val scaleFactor = 0f
    }

    @get:Rule
    val composeRule = createComposeRule(effectContext = ZeroScale)

    private val three = listOf(
        UnderlineTab("New today", "t.new", count = 8),
        UnderlineTab("Friends", "t.friends", count = 5),
        UnderlineTab("Yours", "t.yours"),
    )

    private val colours = listOf(Color.Red, Color.Blue, Color.Green)
    private val composed = mutableSetOf<Int>()

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
        Box(Modifier.fillMaxSize().testTag("page.$page").drawBehind { drawRect(colours[page]) })
    }

    private fun show() {
        animatorScale(0f)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Box(Modifier.size(width = 360.dp, height = 400.dp).background(Color.White)) {
                    UnderlineTabPager(three, onReselect = {}) { Page(it) }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(100)
        composed.clear()
    }

    private fun exists(page: Int) = runCatching { composeRule.onNodeWithTag("page.$page").assertExists() }.isSuccess

    private fun pageLeft(page: Int) = composeRule.onNodeWithTag("page.$page").getUnclippedBoundsInRoot().left.value

    private fun indicatorX() = composeRule.onNodeWithTag("underline.tabs.indicator").getUnclippedBoundsInRoot().left.value

    private fun tabWidth() = composeRule.onNodeWithTag("underline.tabs").getUnclippedBoundsInRoot().width.value / 3f

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

    @Test
    fun `a tap at animator scale 0 lands in one frame`() {
        show()

        composeRule.onNodeWithTag("t.yours").performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.onNodeWithTag("t.yours").assertIsSelected()
        assertTrue("Yours is in the tree", exists(2))
        assertFalse("New today is gone", exists(0))
        assertFalse("Friends never was: $composed", 1 in composed)
        assertEquals(1f, alphaOf(2), 0.02f)
        assertEquals(0f, pageLeft(2), 0.5f)
        assertEquals(2f * tabWidth(), indicatorX(), 1f)
    }

    @Test
    fun `a swipe held at 25, 50 and 75 percent shifts no page and still fades through`() {
        show()
        val distance = composeRule.onNodeWithTag("underline.pager").getUnclippedBoundsInRoot().width.value * composeRule.density.density
        val alphas = mutableListOf<Float>()

        composeRule.onNodeWithTag("underline.pager").performTouchInput { down(center) }
        var held = 0f
        listOf(0.25f, 0.5f, 0.75f).forEach { fraction ->
            composeRule.onNodeWithTag("underline.pager").performTouchInput {
                moveBy(Offset(-distance * (fraction - held), 0f), delayMillis = 400)
            }
            held = fraction
            composeRule.mainClock.advanceTimeByFrame()
            listOf(0, 1).filter { exists(it) }.forEach { assertEquals("page $it moved at $fraction", 0f, pageLeft(it), 0.5f) }
            alphas += if (fraction < 0.5f) alphaOf(0) else alphaOf(1)
        }

        assertTrue("the page under the finger is half gone at a quarter (${alphas[0]})", alphas[0] in 0.4f..0.65f)
        assertTrue("and the one coming in is half there at three quarters (${alphas[2]})", alphas[2] in 0.3f..0.55f)
    }

    @Test
    fun `the bar jumps to the tab a swipe is heading for`() {
        show()
        val distance = composeRule.onNodeWithTag("underline.pager").getUnclippedBoundsInRoot().width.value * composeRule.density.density * 0.6f

        composeRule.onNodeWithTag("underline.pager").performTouchInput {
            down(center)
            moveBy(Offset(-distance, 0f), delayMillis = 400)
        }
        composeRule.mainClock.advanceTimeByFrame()

        assertEquals(1f * tabWidth(), indicatorX(), 1f)
    }
}
