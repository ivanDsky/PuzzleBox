package com.ivandsky.puzzlebox.games.strandsui.chrome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsDisplayed
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.games.strandsui.HintUi
import com.ivandsky.puzzlebox.games.strandsui.StrandsMotion
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Hint button's feedback: progress dots, the count badge, the "+1" and the earned-hint light-up. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StrandsHintFeedbackTest {

    @get:Rule
    val rule = createComposeRule()

    private fun show(hint: MutableState<HintUi>, reduceMotion: Boolean) {
        rule.mainClock.autoAdvance = false
        rule.setContent { PuzzleBoxTheme(darkTheme = false) { Control(hint, reduceMotion) } }
        rule.mainClock.advanceTimeByFrame()
    }

    @Composable
    private fun Control(hint: MutableState<HintUi>, reduceMotion: Boolean) {
        StrandsHintControl(hint.value, onHint = {}, reduceMotion = reduceMotion)
    }

    /** With the clock paused nothing flushes a test-thread write, so the recomposer would never see it. */
    private fun MutableState<HintUi>.post(value: HintUi) {
        this.value = value
        Snapshot.sendApplyNotifications()
    }

    private companion object {
        /** The face lags a posted hint by an effect launch and a recomposition, so two frames. */
        const val FRAME_MS = 16L
    }

    /** The button merges its children's semantics, so the dots are only addressable in the unmerged tree. */
    private fun filled() = rule.onAllNodesWithTag("strands.hint_dot.on", useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    fun `the dots show the thirds toward the next hint`() {
        val hint = mutableStateOf(HintUi(count = 0, meterThirds = 0))
        show(hint, reduceMotion = true)
        for (thirds in 0..2) {
            hint.post(HintUi(count = 0, meterThirds = thirds))
            rule.mainClock.advanceTimeBy(2 * FRAME_MS)
            assertEquals(thirds, filled())
            rule.onAllNodesWithTag("strands.hint_dot.off", useUnmergedTree = true).assertCountEquals(HINT_DOTS - thirds)
        }
    }

    @Test
    fun `the badge shows only while a hint is banked`() {
        val hint = mutableStateOf(HintUi(count = 0, meterThirds = 1))
        show(hint, reduceMotion = true)
        rule.onAllNodesWithTag("strands.hint_badge").assertCountEquals(0)
        hint.post(HintUi(count = 2, meterThirds = 0))
        rule.mainClock.advanceTimeBy(MotionTokens.DurationMedium + 50L)
        rule.onNodeWithTag("strands.hint_badge").assertIsDisplayed()
    }

    @Test
    fun `a non-theme word fills a dot and rises a plus one that then clears`() {
        val hint = mutableStateOf(HintUi(count = 0, meterThirds = 0))
        show(hint, reduceMotion = false)
        assertEquals(0, bonusCount())
        hint.post(HintUi(count = 0, meterThirds = 1))
        rule.mainClock.advanceTimeBy(MotionTokens.BonusRiseDuration / 2L)
        assertEquals(1, filled())
        assertEquals(1, bonusCount())
        rule.mainClock.advanceTimeBy(MotionTokens.BonusRiseDuration.toLong())
        assertEquals(0, bonusCount())
    }

    @Test
    fun `spending a hint does not rise a plus one`() {
        val hint = mutableStateOf(HintUi(count = 1, meterThirds = 0))
        show(hint, reduceMotion = false)
        hint.post(HintUi(count = 0, meterThirds = 0))
        rule.mainClock.advanceTimeBy(MotionTokens.BonusRiseDuration / 2L)
        assertEquals(0, bonusCount())
    }

    @Test
    fun `under reduce motion the plus one holds still then clears`() {
        val hint = mutableStateOf(HintUi(count = 0, meterThirds = 0))
        show(hint, reduceMotion = true)
        hint.post(HintUi(count = 0, meterThirds = 1))
        rule.mainClock.advanceTimeBy(MotionTokens.BonusReducedHold / 2L)
        assertEquals(1, bonusCount())
        rule.mainClock.advanceTimeBy(MotionTokens.BonusReducedHold.toLong())
        assertEquals(0, bonusCount())
    }

    @Test
    fun `the third word shows three dots first, then lights the button`() {
        val hint = mutableStateOf(HintUi(count = 0, meterThirds = 2))
        show(hint, reduceMotion = false)
        hint.post(HintUi(count = 1, meterThirds = 0))
        rule.mainClock.advanceTimeBy(2 * FRAME_MS)
        assertEquals(HINT_DOTS, filled())
        rule.onAllNodesWithTag("strands.hint_badge").assertCountEquals(0)
        rule.mainClock.advanceTimeBy((MotionTokens.DotFillDuration + StrandsMotion.LightDelayMs).toLong() + 50L)
        assertEquals(0, filled())
        rule.mainClock.advanceTimeBy(MotionTokens.DurationMedium + 50L)
        rule.onNodeWithTag("strands.hint_badge").assertIsDisplayed()
    }

    @Test
    fun `under reduce motion an earned hint lights at once`() {
        val hint = mutableStateOf(HintUi(count = 0, meterThirds = 2))
        show(hint, reduceMotion = true)
        hint.post(HintUi(count = 1, meterThirds = 0))
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        assertEquals(0, filled())
        rule.onNodeWithTag("strands.hint_badge").assertIsDisplayed()
    }

    private fun bonusCount() = rule.onAllNodesWithTag("strands.hint_bonus").fetchSemanticsNodes().size
}
