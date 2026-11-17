package com.ivandsky.puzzlebox.designsystem

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// nav-rethink M12-2 (ticket 121): the swipeable underline tab pager, on the test clock.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class UnderlineTabPagerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val three = listOf(
        UnderlineTab("New today", "t.new", count = 8),
        UnderlineTab("Friends", "t.friends", count = 5),
        UnderlineTab("Yours", "t.yours"),
    )

    private val reselected = mutableListOf<Int>()

    private fun animatorScale(scale: Float) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    private fun show(initialPage: Int = 0, content: @Composable (Int) -> Unit = { Box(Modifier.fillMaxSize().testTag("page.$it")) }) {
        composeRule.setContent {
            PuzzleBoxTheme {
                Box(Modifier.size(width = 360.dp, height = 400.dp)) {
                    UnderlineTabPager(three, onReselect = { reselected += it }, initialPage = initialPage, content = content)
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun pageLeft(page: Int) = composeRule.onNodeWithTag("page.$page").getUnclippedBoundsInRoot().left.value

    /** Composed and placed; a neighbour the pager only prefetched has no bounds yet. */
    private fun isShown(page: Int) = runCatching { composeRule.onNodeWithTag("page.$page").getUnclippedBoundsInRoot().left.value }.getOrNull()?.isNaN() == false

    private fun indicatorX() = composeRule.onNodeWithTag("underline.tabs.indicator").getUnclippedBoundsInRoot().left.value

    private fun tabWidth() = composeRule.onNodeWithTag("underline.tabs").getUnclippedBoundsInRoot().width.value / 3f

    private fun pagerWidth() = composeRule.onNodeWithTag("underline.pager").getUnclippedBoundsInRoot().width.value

    private fun advance(ms: Long) = composeRule.mainClock.advanceTimeBy(ms)

    /** A finger drag of [fraction] of a page to the left, then a held release when [slow]. */
    private fun dragLeft(fraction: Float, slow: Boolean) {
        val distance = pagerWidth() * fraction * composeRule.density.density
        composeRule.onNodeWithTag("underline.pager").performTouchInput {
            down(center)
            if (slow) {
                moveBy(Offset(-distance, 0f), delayMillis = 400)
                advanceEventTime(600)
            } else {
                moveBy(Offset(-distance, 0f), delayMillis = 16)
            }
            up()
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `at rest only the current page is composed and the initial page is the one shown`() {
        show(initialPage = 1)

        composeRule.onNodeWithTag("page.1").assertExists()
        composeRule.onNodeWithTag("page.0").assertDoesNotExist()
        composeRule.onNodeWithTag("page.2").assertDoesNotExist()
        composeRule.onNodeWithTag("t.friends").assertIsSelected()
        assertEquals(1f * tabWidth(), indicatorX(), 1f)
    }

    @Test
    fun `a tap on another tab slides the pages and the bar over 300ms and lands there`() {
        animatorScale(1f)
        composeRule.mainClock.autoAdvance = false
        show()
        val width = pagerWidth()

        composeRule.onNodeWithTag("t.friends").performClick()
        composeRule.mainClock.advanceTimeByFrame()
        advance(100)
        val at = pageLeft(0)
        assertTrue("page 0 at $at should be mid-slide", at < -0.05f * width && at > -0.95f * width)
        val bar = indicatorX()
        assertTrue("bar at $bar should be mid-slide", bar > 0.1f * tabWidth() && bar < 0.95f * tabWidth())

        advance(400)
        composeRule.waitForIdle()
        assertEquals(0f, pageLeft(1), 1f)
        assertTrue("page 0 is off to the left", !isShown(0) || pageLeft(0) <= -pagerWidth() + 1f)
        composeRule.onNodeWithTag("t.friends").assertIsSelected()
        assertEquals(1f * tabWidth(), indicatorX(), 1f)
        assertTrue("a tap on another tab is not a reselect", reselected.isEmpty())
    }

    @Test
    fun `a drag past half a page settles on the next page and the bar follows the finger`() {
        animatorScale(1f)
        composeRule.mainClock.autoAdvance = false
        show()

        val distance = pagerWidth() * 0.6f * composeRule.density.density
        composeRule.onNodeWithTag("underline.pager").performTouchInput {
            down(center)
            moveBy(Offset(-distance, 0f), delayMillis = 400)
        }
        composeRule.mainClock.advanceTimeByFrame()
        assertEquals(0.6f * tabWidth(), indicatorX(), 0.05f * tabWidth())
        composeRule.onNodeWithTag("underline.pager").performTouchInput { advanceEventTime(600); up() }
        advance(600)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("t.friends").assertIsSelected()
        assertEquals(0f, pageLeft(1), 1f)
        assertEquals(1f * tabWidth(), indicatorX(), 1f)
    }

    @Test
    fun `a drag under half a page returns to the first page`() {
        animatorScale(1f)
        composeRule.mainClock.autoAdvance = false
        show()

        dragLeft(0.25f, slow = true)
        advance(600)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("t.new").assertIsSelected()
        assertTrue("page 1 is back off to the right", !isShown(1) || pageLeft(1) >= pagerWidth() - 1f)
        assertEquals(0f, pageLeft(0), 1f)
        assertEquals(0f, indicatorX(), 1f)
    }

    @Test
    fun `under reduced motion a tap crossfades in place and the bar jumps`() {
        animatorScale(0f)
        composeRule.mainClock.autoAdvance = false
        show()

        composeRule.onNodeWithTag("t.friends").performClick()
        composeRule.mainClock.advanceTimeByFrame()
        assertEquals(1f * tabWidth(), indicatorX(), 1f)
        listOf(16L, 32L, 48L).forEach { step ->
            advance(step)
            listOf(0, 1).forEach { page ->
                if (isShown(page)) {
                    assertEquals("page $page moved", 0f, pageLeft(page), 0.5f)
                }
            }
        }
        advance(300)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("t.friends").assertIsSelected()
        composeRule.onNodeWithTag("page.0").assertDoesNotExist()
        assertEquals(0f, pageLeft(1), 0.5f)
    }

    @Test
    fun `under reduced motion both pages are shown while they fade, neither offset`() {
        animatorScale(0f)
        composeRule.mainClock.autoAdvance = false
        show()
        val width = pagerWidth()
        val distance = width * 0.3f * composeRule.density.density

        composeRule.onNodeWithTag("underline.pager").performTouchInput {
            down(center)
            moveBy(Offset(-distance, 0f), delayMillis = 400)
        }
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.onNodeWithTag("page.0").assertExists()
        composeRule.onNodeWithTag("page.1").assertExists()
        assertEquals(0f, pageLeft(0), 0.5f)
        assertEquals(0f, pageLeft(1), 0.5f)
    }

    @Test
    fun `under reduced motion a swipe still changes the tab`() {
        animatorScale(0f)
        composeRule.mainClock.autoAdvance = false
        show()

        dragLeft(0.7f, slow = false)
        advance(400)
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("t.friends").assertIsSelected()
        composeRule.onNodeWithTag("page.0").assertDoesNotExist()
        assertEquals(0f, pageLeft(1), 0.5f)
        assertEquals(1f * tabWidth(), indicatorX(), 1f)
    }

    @Test
    fun `tapping the selected tab reports its index and moves nothing`() {
        animatorScale(1f)
        composeRule.mainClock.autoAdvance = false
        show(initialPage = 1)

        composeRule.onNodeWithTag("t.friends").performClick()
        composeRule.mainClock.advanceTimeByFrame()
        advance(100)
        composeRule.waitForIdle()

        assertEquals(listOf(1), reselected)
        assertEquals(0f, pageLeft(1), 0.5f)
        assertEquals(1f * tabWidth(), indicatorX(), 0.5f)
        composeRule.onNodeWithTag("t.new").assertIsNotSelected()
    }

    @Test
    fun `the selected page survives recreation`() {
        val tester = StateRestorationTester(composeRule)
        tester.setContent {
            PuzzleBoxTheme {
                Box(Modifier.size(width = 360.dp, height = 400.dp)) {
                    UnderlineTabPager(three, onReselect = {}) { Box(Modifier.fillMaxSize().testTag("page.$it")) }
                }
            }
        }
        composeRule.onNodeWithTag("t.yours").performClick()
        composeRule.waitForIdle()

        tester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithTag("t.yours").assertIsSelected()
        composeRule.onNodeWithTag("page.2").assertExists()
    }
}
