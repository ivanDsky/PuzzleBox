package com.ivandsky.puzzlebox.designsystem

import android.provider.Settings
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Nav-rethink 129, the solve moment's one clock: the pop and the frame lap are functions of the milliseconds since the
 * solve, and the clock runs to 1300 ms and stops. Sampled at the ticket's 440, 500, 900, 1300 and 1500 ms.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SolveMomentStateTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var state: SolveMomentState
    private var plays by mutableIntStateOf(0)

    @After
    fun restoreMotion() = setAnimatorScale(1f)

    private fun setAnimatorScale(scale: Float) {
        val resolver = RuntimeEnvironment.getApplication().contentResolver
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, scale)
    }

    private fun setState() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            state = rememberSolveMomentState()
            LaunchedEffect(plays) { if (plays > 0) state.play() else state.reset() }
        }
        composeRule.mainClock.advanceTimeBy(FRAME_MS)
    }

    private fun play() {
        plays++
        Snapshot.sendApplyNotifications()
    }

    @Test
    fun `the board is still until 440 ms, at 1,03 at 560 ms, and exactly at rest again by 900 ms`() {
        assertEquals(1f, solvePopScale(0f), 0f)
        assertEquals(1f, solvePopScale(440f), 0f)
        assertTrue("rising at 500 ms", solvePopScale(500f) > 1.015f && solvePopScale(500f) < CelebrationMotion.BoardPopScale)
        assertEquals(CelebrationMotion.BoardPopScale, solvePopScale(560f), 0.0001f)
        assertEquals(1f, solvePopScale(900f), 0f)
        assertEquals(1f, solvePopScale(1300f), 0f)
        assertEquals(1f, solvePopScale(1500f), 0f)
    }

    @Test
    fun `the pop rises without a dip and releases with one small undershoot`() {
        val rise = (440..560).map { solvePopScale(it.toFloat()) }
        assertTrue("the rise dips", rise.zipWithNext().all { (a, b) -> b >= a })

        val release = (560..900).map { solvePopScale(it.toFloat()) }
        assertTrue("the release overshoots the peak", release.all { it <= CelebrationMotion.BoardPopScale + 0.0001f })
        val lowest = release.min()
        assertTrue("the spring undershoots a little once, got $lowest", lowest < 1f && lowest > 0.995f)
    }

    @Test
    fun `the frame lap is steady from 500 ms and closed at 1300 ms`() {
        assertEquals(0f, solveFrameLap(440f), 0f)
        assertEquals(0f, solveFrameLap(500f), 0f)
        assertEquals(0.5f, solveFrameLap(900f), 0.0001f)
        assertEquals(1f, solveFrameLap(1300f), 0f)
        assertEquals(1f, solveFrameLap(1500f), 0f)
    }

    @Test
    fun `the comet is whole until the last 8 percent of the lap, then fades to nothing`() {
        assertEquals(1f, solveCometAlpha(0.1f), 0f)
        assertEquals(1f, solveCometAlpha(0.92f), 0.0001f)
        assertEquals(0.5f, solveCometAlpha(0.96f), 0.0001f)
        assertEquals(0f, solveCometAlpha(1f), 0f)
    }

    @Test
    fun `nothing is playing until play`() {
        setState()
        assertFalse(state.playing)
        assertEquals(1f, state.popScale, 0f)
    }

    @Test
    fun `the clock counts the milliseconds since play, stops at 1300 ms and stays there`() {
        setState()
        play()
        var at = 0L
        for (ms in listOf(440L, 500L, 900L)) {
            composeRule.mainClock.advanceTimeBy(ms - at)
            at = ms
            assertTrue("at $ms ms the clock reads ${state.ms}", state.ms in (ms - 2 * FRAME_MS).toFloat()..ms.toFloat())
        }
        assertEquals("900 ms: the pop is over", 1f, state.popScale, 0f)

        composeRule.mainClock.advanceTimeBy(1_300 + 2 * FRAME_MS - at)
        assertEquals("the motion ends when the frame closes", 1_300f, state.ms, 0f)

        composeRule.mainClock.advanceTimeBy(200)
        assertEquals("nothing moves from 1300 ms to the hand-off", 1_300f, state.ms, 0f)
        assertTrue(state.playing)
    }

    @Test
    fun `reduced motion is at the end from the start`() {
        setAnimatorScale(0f)
        setState()
        play()
        composeRule.mainClock.advanceTimeBy(FRAME_MS)
        assertTrue(state.playing)
        assertEquals(1_300f, state.ms, 0f)
        assertEquals(1f, state.popScale, 0f)
    }

    @Test
    fun `reset clears the moment and the next play starts from nothing`() {
        setState()
        play()
        composeRule.mainClock.advanceTimeBy(2_000)
        plays = 0
        Snapshot.sendApplyNotifications()
        composeRule.mainClock.advanceTimeBy(FRAME_MS)
        assertFalse(state.playing)

        play()
        composeRule.mainClock.advanceTimeBy(2 * FRAME_MS)
        assertTrue(state.playing)
        assertTrue("the second play starts over, the clock reads ${state.ms}", state.ms < 3 * FRAME_MS)
    }

    private companion object {
        const val FRAME_MS = 16L
    }
}
