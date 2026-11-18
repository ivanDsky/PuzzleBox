package com.ivandsky.puzzlebox.designsystem

import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Ticket 1509: a scrolling list inside the tab pager moved on its own after the finger lifted. A fling that ran into the
 * list's edge hands its leftover velocity up the nested-scroll chain, and the pager took it for a settle of its own: a
 * 300 ms tween from where it already was to where it already was. The list's stretch (its [OverscrollEffect]) is applied
 * only after that returns, so the list sat at its edge for the whole settle and then bounced with nobody touching it.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class UnderlineTabPagerListFlingTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val tabs = listOf(UnderlineTab("One", "t.one"), UnderlineTab("Two", "t.two"))
    private val frame = 16L

    /** Notes when a fling's leftover reaches the list's overscroll: where the edge's stretch would begin. */
    private class FlingProbe(private val now: () -> Long) : OverscrollEffect {
        var leftoverAt: Long? = null
        override fun applyToScroll(delta: Offset, source: NestedScrollSource, performScroll: (Offset) -> Offset) = performScroll(delta)
        override suspend fun applyToFling(velocity: Velocity, performFling: suspend (Velocity) -> Velocity) {
            val left = velocity - performFling(velocity)
            if (left.y != 0f) leftoverAt = now()
        }
        override val isInProgress = false
    }

    private lateinit var list: LazyListState
    private val probe = FlingProbe { composeRule.mainClock.currentTime }

    private fun show(firstItem: Int) {
        composeRule.setContent {
            PuzzleBoxTheme {
                list = rememberLazyListState(initialFirstVisibleItemIndex = firstItem)
                Box(Modifier.size(width = 360.dp, height = 500.dp)) {
                    UnderlineTabPager(tabs, onReselect = {}) { page ->
                        if (page == 0) {
                            LazyColumn(Modifier.fillMaxSize().testTag("list"), state = list, overscrollEffect = probe) {
                                items(ROWS) { Box(Modifier.fillMaxWidth().height(48.dp)) }
                            }
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
    }

    /** Flings with [gesture], then returns how long after the list stopped at the edge [landed] its overscroll was told. */
    private fun overscrollLag(landed: () -> Boolean, gesture: TouchInjectionScope.() -> Unit): Long {
        composeRule.onNodeWithTag("list").performTouchInput(gesture)
        var landedAt: Long? = null
        repeat(200) {
            composeRule.mainClock.advanceTimeBy(frame)
            if (landedAt == null && landed()) landedAt = composeRule.mainClock.currentTime
        }
        val at = landedAt
        val told = probe.leftoverAt
        assertNotNull("the fling never reached the list's edge", at)
        assertNotNull("the overscroll never heard of the edge", told)
        return told!! - at!!
    }

    @Test
    fun `a fling into the list's top edge reaches its overscroll as the list lands, not a settle later`() {
        show(firstItem = 20)
        val lag = overscrollLag(landed = { list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset == 0 }) {
            swipeDown(durationMillis = 100)
        }
        assertTrue("overscroll heard of the top edge $lag ms after the list landed", lag <= 2 * frame)
    }

    @Test
    fun `a fling into the list's bottom edge reaches its overscroll as the list lands, not a settle later`() {
        show(firstItem = ROWS - 20)
        val lag = overscrollLag(landed = { !list.canScrollForward }) { swipeUp(durationMillis = 100) }
        assertTrue("overscroll heard of the bottom edge $lag ms after the list landed", lag <= 2 * frame)
    }

    @Test
    fun `a release part of the way along a page still glides back over its settle`() {
        show(firstItem = 0)
        val tabWidth = composeRule.onNodeWithTag("underline.tabs").getUnclippedBoundsInRoot().width.value / tabs.size
        fun bar() = composeRule.onNodeWithTag("underline.tabs.indicator").getUnclippedBoundsInRoot().left.value
        composeRule.onNodeWithTag("underline.pager").performTouchInput {
            down(center)
            moveBy(Offset(-width * 0.3f, 0f), delayMillis = 400)
            advanceEventTime(600)
            up()
        }
        composeRule.mainClock.advanceTimeBy(3 * frame)
        assertTrue("the bar is at ${bar()}, still on its way back", bar() > 0.02f * tabWidth && bar() < 0.3f * tabWidth)
        composeRule.mainClock.advanceTimeBy(MotionTokens.DurationLong.toLong() + frame)
        assertEquals(0f, bar(), 1f)
    }

    private companion object {
        const val ROWS = 80
    }
}
