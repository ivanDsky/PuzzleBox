package com.ivandsky.puzzlebox.gameui

import androidx.compose.runtime.MonotonicFrameClock
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shared trace line (spec A1/A4), frame by frame on a virtual clock. Cells are ints on one
 * row and a direction is +1 or -1, so a line [0, 1, 2] ends at index 2.
 */
class TraceLineMotionTest {

    @Test
    fun `cells added in one event grow in over GrowDuration`() = runTest {
        val motion = motion()
        assertEquals(0f, motion.step(listOf(0), tip = null, animate = true))

        // The tween's coroutine has not run yet: the first frame still shows the old end.
        assertEquals(0f, motion.step(listOf(0, 1, 2), tip = null, animate = true))
        val ends = frames(MotionTokens.Trace.GrowDuration + 3 * FRAME_MS) { motion.step(listOf(0, 1, 2), tip = null, animate = true) }

        ends.zipWithNext().forEach { (a, b) -> assertTrue("grows monotonically: $a then $b", b >= a) }
        val mid = ends[(MotionTokens.Trace.GrowDuration / 2 / FRAME_MS).toInt()]
        assertTrue("mid-grow end $mid", mid > 1f && mid < 2f)
        assertEquals("settled one frame past GrowDuration", 2f, ends[((MotionTokens.Trace.GrowDuration + FRAME_MS) / FRAME_MS).toInt()])
    }

    @Test
    fun `a backtrack under a back tip retracts over RetractDuration, faster than a grow`() = runTest {
        val motion = motion()
        motion.step(listOf(0, 1, 2), tip = null, animate = false)
        val back = TraceTip(TipKind.Back, direction = -1, extent = 0.5f)

        assertEquals(1f, motion.step(listOf(0, 1), back, animate = true))
        val ends = frames(MotionTokens.Trace.RetractDuration + 3 * FRAME_MS) { motion.step(listOf(0, 1), back, animate = true) }

        ends.zipWithNext().forEach { (a, b) -> assertTrue("retracts monotonically: $a then $b", b <= a) }
        assertTrue("still moving one frame in: ${ends.first()}", ends.first() > 0.5f)
        assertEquals(0.5f, ends.last())
        assertTrue(MotionTokens.Trace.RetractDuration < MotionTokens.Trace.GrowDuration)
    }

    @Test
    fun `on lift the tip returns home along its cell over HomeDuration`() = runTest {
        val motion = motion()
        val reach = TraceTip(TipKind.Forward, direction = 1, extent = 0.5f)
        motion.step(listOf(0, 1), tip = null, animate = true)
        assertEquals(1.5f, motion.step(listOf(0, 1), reach, animate = true))
        assertEquals(2, motion.tipCell)

        assertEquals(1.5f, motion.step(listOf(0, 1), tip = null, animate = true))
        val ends = frames(MotionTokens.Trace.HomeDuration + 3 * FRAME_MS) { motion.step(listOf(0, 1), tip = null, animate = true) }

        val mid = ends[2]
        assertTrue("mid-home end $mid", mid > 1f && mid < 1.5f)
        assertEquals(1f, ends.last())
        assertEquals("the tip cell stays on the line to retract along", 2, motion.tipCell)
    }

    @Test
    fun `the head pops on every cell joined and settles within PopDuration`() = runTest {
        val motion = motion()
        motion.step(listOf(0), tip = null, animate = true)
        motion.step(listOf(0, 1), tip = null, animate = true)
        val heads = frames(MotionTokens.Trace.PopDuration + 4 * FRAME_MS) {
            motion.step(listOf(0, 1), tip = null, animate = true)
            motion.headScale
        }

        val peak = heads.indexOfFirst { it == MotionTokens.Trace.PopScale }
        val rise = (MotionTokens.Trace.PopDuration * MotionTokens.Trace.PopRiseFraction).toInt()
        assertEquals("peaks one clock-start frame after the rise", rise / FRAME_MS.toInt(), peak)
        assertEquals(1f, heads.last())
    }

    @Test
    fun `a shorter path does not pop`() = runTest {
        val motion = motion()
        motion.step(listOf(0, 1, 2), tip = null, animate = true)
        motion.step(listOf(0, 1), tip = null, animate = true)
        val heads = frames(MotionTokens.Trace.PopDuration.toLong()) {
            motion.step(listOf(0, 1), tip = null, animate = true)
            motion.headScale
        }
        assertTrue(heads.all { it == 1f })
    }

    @Test
    fun `reduced motion draws the target at once and never pops`() = runTest {
        val motion = motion()
        motion.step(listOf(0), tip = null, animate = false)
        assertEquals(2f, motion.step(listOf(0, 1, 2), tip = null, animate = false))
        testScheduler.advanceTimeBy(MotionTokens.Trace.PopDuration.toLong())
        testScheduler.runCurrent()
        assertEquals(1f, motion.headScale)
    }

    @Test
    fun `an empty path clears the line and the tip cell`() = runTest {
        val motion = motion()
        motion.step(listOf(0, 1), TraceTip(TipKind.Forward, direction = 1, extent = 0.5f), animate = false)
        assertEquals(0f, motion.step(emptyList(), tip = null, animate = true))
        assertNull(motion.tipCell)
    }

    private fun TestScope.motion(): TraceLineMotion<Int, Int> =
        TraceLineMotion(CoroutineScope(backgroundScope.coroutineContext + VirtualFrameClock(testScheduler))) { cell, direction -> cell + direction }

    /** Runs [frame] once per virtual frame for [ms]; the first sample is the frame after the event. */
    private fun <T> TestScope.frames(ms: Long, frame: () -> T): List<T> = buildList {
        repeat((ms / FRAME_MS).toInt()) {
            testScheduler.advanceTimeBy(FRAME_MS)
            testScheduler.runCurrent()
            add(frame())
        }
    }

    private class VirtualFrameClock(private val scheduler: TestCoroutineScheduler) : MonotonicFrameClock {
        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            delay(FRAME_MS)
            return onFrame(scheduler.currentTime * 1_000_000)
        }
    }

    private companion object {
        const val FRAME_MS = 16L
    }
}
