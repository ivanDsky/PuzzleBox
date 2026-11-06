package com.ivandsky.puzzlebox.gameui

import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.TodayStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Fixed card slots (A7): status swaps never move a line; reduced motion is instant and static. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CardTextTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val slots = listOf("slot.name", "slot.status", "slot.meta")

    private val statuses = listOf(
        TodayStatus.NotStarted,
        TodayStatus.InProgress(3_723_000L),
        TodayStatus.Solved(42_000L),
        TodayStatus.Offline,
        null,
    )

    private fun setAnimatorScale(scale: Float) {
        val resolver = RuntimeEnvironment.getApplication().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    @After
    fun resetScale() = setAnimatorScale(1f)

    private fun setCard(initial: TodayStatus?): (TodayStatus?) -> Unit {
        var status by mutableStateOf(initial)
        composeRule.setContent {
            PuzzleBoxTheme {
                Column(Modifier.width(160.dp)) {
                    CardSlot("Mini Sudoku", 22.dp, Modifier.testTag("slot.name")) { Text(it) }
                    CardSlot(status, 40.dp, Modifier.testTag("slot.status")) { shown ->
                        Row {
                            StatusMark(shown, 14.dp, Color.Black)
                            if (shown == null) {
                                SkeletonLine(0.6f, 12.dp, Color.Black)
                            } else {
                                Text(todayStatusLine(shown), maxLines = 2)
                            }
                        }
                    }
                    CardSlot(if (status == null) null else "#411", 16.dp, Modifier.testTag("slot.meta")) {
                        Text(it.orEmpty())
                    }
                }
            }
        }
        return {
            status = it
            // The clock is stepped by hand below, so flush the write before the next frame.
            Snapshot.sendApplyNotifications()
        }
    }

    @Test
    fun `swapping the status keeps every slot's bounds`() {
        val swap = setCard(statuses.first())
        val before = slots.map { composeRule.onNodeWithTag(it).getBoundsInRoot() }
        // A shimmering skeleton never idles, so step the clock rather than wait for idle.
        composeRule.mainClock.autoAdvance = false
        statuses.forEach { next ->
            swap(next)
            composeRule.mainClock.advanceTimeBy(1_000L)
            composeRule.onNodeWithTag(statusMarkTag(next)).assertExists()
            assertEquals("after $next", before, slots.map { composeRule.onNodeWithTag(it).getBoundsInRoot() })
        }
    }

    @Test
    fun `a value change cross-fades both marks for a moment`() {
        val swap = setCard(TodayStatus.NotStarted)
        composeRule.mainClock.autoAdvance = false
        swap(TodayStatus.Solved(42_000L))
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(40L)
        composeRule.onNodeWithTag(statusMarkTag(TodayStatus.NotStarted)).assertExists()
        composeRule.onNodeWithTag(statusMarkTag(TodayStatus.Solved(0))).assertExists()
    }

    @Test
    fun `reduced motion swaps a value with no cross-fade`() {
        setAnimatorScale(0f)
        val swap = setCard(TodayStatus.NotStarted)
        composeRule.mainClock.autoAdvance = false
        swap(TodayStatus.Solved(42_000L))
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag(statusMarkTag(TodayStatus.NotStarted)).assertDoesNotExist()
        composeRule.onNodeWithTag(statusMarkTag(TodayStatus.Solved(0))).assertExists()
    }

    @Test
    fun `skeleton shimmers`() {
        setCard(null)
        composeRule.mainClock.autoAdvance = false
        composeRule.mainClock.advanceTimeByFrame()
        assertEquals(2, composeRule.onAllNodesWithTag(SHIMMER_TAG).fetchSemanticsNodes().size)
    }

    @Test
    fun `skeleton is static under reduced motion`() {
        setAnimatorScale(0f)
        setCard(null)
        composeRule.onNodeWithTag(statusMarkTag(null)).assertExists()
        composeRule.onNodeWithTag(SHIMMER_TAG).assertDoesNotExist()
    }
}
