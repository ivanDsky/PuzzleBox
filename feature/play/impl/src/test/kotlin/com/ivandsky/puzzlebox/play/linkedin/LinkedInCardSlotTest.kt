package com.ivandsky.puzzlebox.play.linkedin

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInBoardView
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInDayStatus
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInDayView
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.play.libraryCardColors
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The chip across a day switch: it keeps drawing the last settled status while the next day is read, so a switch never
 * reads as synced, syncing, synced. The clock is paused and stepped by hand, so the timings are exact.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class LinkedInCardSlotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val view = MutableStateFlow<LinkedInDayView?>(null)
    private var retries = 0
    private val synced = LinkedInDayStatus.Imported(52_000L, 0)

    @After
    fun resetScale() = setAnimatorScale(1f)

    private fun setAnimatorScale(scale: Float) {
        val resolver = RuntimeEnvironment.getApplication().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    private fun show(first: LinkedInDayStatus?) {
        view.value = first?.let(::dayView)
        composeRule.setContent {
            PuzzleBoxTheme {
                Box(Modifier.background(libraryCardColors(GameBrand(AccentSlot.Blue, GameGlyphs.Zip)).fill)) {
                    LinkedInCardSlot(LinkedInCardState(view, onResume = {}, onRetry = { retries++ }, onReconnect = null))
                }
            }
        }
        composeRule.mainClock.autoAdvance = false
        composeRule.mainClock.advanceTimeBy(2_000L)
    }

    private fun dayView(status: LinkedInDayStatus) = LinkedInDayView(status, LinkedInBoardView.Hidden)

    private fun emit(status: LinkedInDayStatus?) {
        view.value = status?.let(::dayView)
        Snapshot.sendApplyNotifications()
    }

    private fun chips() = composeRule.onAllNodesWithTag("linkedin.sync", useUnmergedTree = true).fetchSemanticsNodes().size

    private fun rings() = composeRule.onAllNodesWithTag("linkedin.sync.ring", useUnmergedTree = true).fetchSemanticsNodes().size

    private fun words() = composeRule.onNodeWithTag("linkedin.sync").fetchSemanticsNode().config
        .getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }

    /** Steps [ms] a frame at a time, checking every frame. */
    private fun run(ms: Long, check: (Long) -> Unit) {
        var at = 0L
        while (at < ms) {
            composeRule.mainClock.advanceTimeBy(16L)
            at += 16L
            check(at)
        }
    }

    @Test
    fun `a day switch that reads for under a second never shows syncing or a ring`() {
        show(synced)
        emit(LinkedInDayStatus.Loading)
        run(500L) { assertSteady(it) }
        emit(synced)
        run(2_000L) { assertSteady(it) }
    }

    private fun assertSteady(at: Long) {
        assertEquals("one chip at $at ms", 1, chips())
        assertEquals("no ring at $at ms", 0, rings())
        assertEquals("no words at $at ms", emptyList<String>(), words())
        composeRule.onNodeWithTag("linkedin.sync").assertContentDescriptionEquals("Synced with LinkedIn")
    }

    @Test
    fun `a read past a second swaps the check for a ring in place, with no label`() {
        show(synced)
        emit(LinkedInDayStatus.Loading)
        run(900L) { assertSteady(it) }
        run(600L) { }
        assertEquals(1, rings())
        assertEquals(emptyList<String>(), words())
        composeRule.onNodeWithTag("linkedin.sync").assertContentDescriptionEquals("Syncing with LinkedIn")
        emit(synced)
        run(1_000L) { }
        assertEquals(0, rings())
        composeRule.onNodeWithTag("linkedin.sync").assertContentDescriptionEquals("Synced with LinkedIn")
    }

    @Test
    fun `the first read, with nothing settled, shows the logo and a ring at once`() {
        show(LinkedInDayStatus.Loading)
        assertEquals(1, chips())
        assertEquals(1, rings())
        assertEquals(emptyList<String>(), words())
    }

    @Test
    fun `a settled change to offline shows retry`() {
        show(synced)
        emit(LinkedInDayStatus.Loading)
        run(300L) { }
        emit(LinkedInDayStatus.Offline)
        run(1_000L) { }
        assertEquals(listOf("Retry"), words())
        assertEquals(0, rings())
        composeRule.onNodeWithTag("linkedin.sync").assertHasClickAction()
    }

    @Test
    fun `a view that goes null between days keeps the chip`() {
        show(synced)
        emit(null)
        run(1_500L) { assertSteady(it) }
        emit(LinkedInDayStatus.Loading)
        run(300L) { assertSteady(it) }
        emit(LinkedInDayStatus.DidNotPlay)
        run(1_000L) { assertSteady(it) }
    }

    @Test
    fun `reduced motion swaps the settled change at once`() {
        setAnimatorScale(0f)
        show(synced)
        emit(LinkedInDayStatus.Offline)
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        assertEquals(listOf("Retry"), words())
        assertTrue(chips() == 1)
    }
}
