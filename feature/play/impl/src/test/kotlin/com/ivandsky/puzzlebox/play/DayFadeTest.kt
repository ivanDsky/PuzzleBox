package com.ivandsky.puzzlebox.play

import android.provider.Settings
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
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

/** The day fade-through: the old day out over 150 ms, then the new day in over 300 ms rising 8 dp. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h800dp")
class DayFadeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var fade: DayFade<String>

    private class Frame(val drawn: String, val opacity: Float, val rise: Float)

    private fun setAnimatorScale(scale: Float) {
        val resolver = RuntimeEnvironment.getApplication().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    @After
    fun resetScale() = setAnimatorScale(1f)

    /** Shows "A" settled and returns the switch to "B"; the clock is paused from the switch on. */
    private fun fadeOnA(): () -> Unit {
        var face by mutableStateOf("A")
        composeRule.setContent {
            PuzzleBoxTheme {
                val current = rememberDayFade(face)
                SideEffect { fade = current }
                current.Slot(40.dp, Modifier.fillMaxSize()) { Text(it, Modifier.testTag("face.$it")) }
            }
        }
        composeRule.mainClock.autoAdvance = false
        composeRule.mainClock.advanceTimeBy(1_000L)
        return {
            face = "B"
            // The clock is stepped by hand, so flush the write before the next frame.
            Snapshot.sendApplyNotifications()
        }
    }

    private fun frame() = Frame(fade.drawn, fade.opacity, fade.rise.value)

    private fun drawn(face: String) = composeRule.onAllNodesWithTag("face.$face").fetchSemanticsNodes().size

    @Test
    fun `halfway through the exit the old day is part faded and the new one is not drawn`() {
        val switchDay = fadeOnA()
        switchDay()
        composeRule.mainClock.advanceTimeBy(MotionTokens.DurationExit / 2L)
        val half = frame()
        assertEquals("A", half.drawn)
        assertTrue("opacity ${half.opacity}", half.opacity > 0f && half.opacity < 1f)
        assertEquals(0f, half.rise, 0f)
        assertEquals(1, drawn("A"))
        assertEquals(0, drawn("B"))
    }

    @Test
    fun `no frame draws both days, sampled every 16 ms over the whole switch`() {
        val switchDay = fadeOnA()
        switchDay()
        repeat(40) { index ->
            composeRule.mainClock.advanceTimeBy(16L)
            assertEquals("faces drawn at ${(index + 1) * 16} ms", 1, drawn("A") + drawn("B"))
        }
    }

    @Test
    fun `the old day leaves over 150 ms and the new one is drawn after that`() {
        val switchDay = fadeOnA()
        switchDay()
        composeRule.mainClock.advanceTimeBy(MotionTokens.DurationExit - 20L)
        assertEquals("A", frame().drawn)
        composeRule.mainClock.advanceTimeBy(60L)
        assertEquals("B", frame().drawn)
    }

    @Test
    fun `the new day rises into place as it fades in and ends settled`() {
        val switchDay = fadeOnA()
        switchDay()
        // Frames, not wall time, decide when the swap lands, so wait for it.
        var waited = 0
        while (frame().drawn != "B" && waited < 20) {
            composeRule.mainClock.advanceTimeBy(16L)
            waited++
        }
        val entering = mutableListOf<Frame>()
        repeat(MotionTokens.DurationLong / 16 - 2) {
            entering += frame()
            composeRule.mainClock.advanceTimeBy(16L)
        }
        assertTrue("every sample is the new day", entering.all { it.drawn == "B" })
        assertTrue("starts below its place", entering.first().rise > 0f)
        assertTrue("never more than the rise", entering.all { it.rise <= MotionTokens.EnterRise.value })
        entering.zipWithNext().forEach { (before, after) ->
            assertTrue("rise only goes up the page", after.rise <= before.rise)
            assertTrue("opacity only grows", after.opacity >= before.opacity)
        }
        assertTrue("mid-enter is part faded", entering.any { it.opacity > 0f && it.opacity < 1f })
        composeRule.mainClock.advanceTimeBy(500L)
        val settled = frame()
        assertEquals("B", settled.drawn)
        assertEquals(1f, settled.opacity, 0f)
        assertEquals(0f, settled.rise, 0f)
    }

    @Test
    fun `reduced motion swaps at once`() {
        setAnimatorScale(0f)
        val switchDay = fadeOnA()
        switchDay()
        composeRule.mainClock.advanceTimeByFrame()
        val now = frame()
        assertEquals("B", now.drawn)
        assertEquals(1f, now.opacity, 0f)
        assertEquals(0f, now.rise, 0f)
        assertEquals(0, drawn("A"))
        assertEquals(1, drawn("B"))
    }
}
