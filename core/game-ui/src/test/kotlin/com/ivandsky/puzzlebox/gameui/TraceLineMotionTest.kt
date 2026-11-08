package com.ivandsky.puzzlebox.gameui

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.snapshots.Snapshot
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shared trace line (spec A1), frame by frame on a virtual clock. Cells are ints on one
 * row, so a line [0, 1, 2] ends at index 2.
 */
class TraceLineMotionTest {

    @Test
    fun `cells added in one event grow in over GrowDuration`() = runTest {
        val motion = motion()
        assertEquals(0f, motion.step(listOf(0), animate = true))

        // The tween's coroutine has not run yet: the first frame still shows the old end.
        assertEquals(0f, motion.step(listOf(0, 1, 2), animate = true))
        val ends = frames(MotionTokens.Trace.GrowDuration + 3 * FRAME_MS) { motion.step(listOf(0, 1, 2), animate = true) }

        ends.zipWithNext().forEach { (a, b) -> assertTrue("grows monotonically: $a then $b", b >= a) }
        val mid = ends[(MotionTokens.Trace.GrowDuration / 2 / FRAME_MS).toInt()]
        assertTrue("mid-grow end $mid", mid > 1f && mid < 2f)
        assertEquals("settled one frame past GrowDuration", 2f, ends[((MotionTokens.Trace.GrowDuration + FRAME_MS) / FRAME_MS).toInt()])
    }

    @Test
    fun `a backtrack retracts over RetractDuration, faster than a grow`() = runTest {
        val motion = motion()
        motion.step(listOf(0, 1, 2), animate = false)

        assertEquals(2f, motion.step(listOf(0, 1), animate = true))
        val ends = frames(MotionTokens.Trace.RetractDuration + 3 * FRAME_MS) { motion.step(listOf(0, 1), animate = true) }

        ends.zipWithNext().forEach { (a, b) -> assertTrue("retracts monotonically: $a then $b", b <= a) }
        assertTrue("still moving one frame in: ${ends.first()}", ends.first() > 1f)
        assertEquals(1f, ends.last())
        assertEquals("the removed cell is off the line once retracted", listOf(0, 1), motion.line)
        assertTrue(MotionTokens.Trace.RetractDuration < MotionTokens.Trace.GrowDuration)
    }

    @Test
    fun `a retracting line keeps the removed cells to retract along`() = runTest {
        val motion = motion()
        motion.step(listOf(0, 1, 2), animate = false)

        motion.step(listOf(0, 1), animate = true)
        assertEquals(listOf(0, 1, 2), motion.line)
        frames(2 * FRAME_MS) { motion.step(listOf(0, 1), animate = true) }
        assertEquals(listOf(0, 1, 2), motion.line)
    }

    @Test
    fun `the end rests on the last committed cell, never between cells`() = runTest {
        val motion = motion()
        motion.step(listOf(0, 1), animate = true)
        val ends = frames(MotionTokens.Trace.GrowDuration.toLong()) { motion.step(listOf(0, 1), animate = true) }
        assertTrue(ends.all { it == 1f })
    }

    @Test
    fun `reduced motion draws the target at once`() = runTest {
        val motion = motion()
        motion.step(listOf(0), animate = false)
        assertEquals(2f, motion.step(listOf(0, 1, 2), animate = false))
    }

    @Test
    fun `an empty path clears the line`() = runTest {
        val motion = motion()
        motion.step(listOf(0, 1), animate = false)
        assertEquals(0f, motion.step(emptyList(), animate = true))
    }

    @Test
    fun `the frame that starts a blend reads the blend state, so the tween redraws the line`() = runTest {
        // A draw reruns only when state it read changes. The frame that grows the line starts a
        // blend and draws its first frame before the tween's coroutine runs; if that frame read
        // no state, the tween's snap and every frame after it would never redraw, and the end
        // would stay where the previous frame left it.
        val motion = motion()
        fun reads(path: List<Int>): Int {
            var count = 0
            val snapshot = Snapshot.takeSnapshot(readObserver = { count++ })
            try {
                snapshot.enter { motion.step(path, animate = true) }
            } finally {
                snapshot.dispose()
            }
            return count
        }

        reads(listOf(0, 1))
        assertTrue("growing frame reads state", reads(listOf(0, 1, 2)) > 0)
        assertTrue("pending frame reads state", reads(listOf(0, 1, 2)) > 0)
        frames(MotionTokens.Trace.GrowDuration / 2L) { reads(listOf(0, 1, 2)) }.forEach { assertTrue("mid-blend frame reads state", it > 0) }
        val backtrack = reads(listOf(0, 1))
        assertTrue("retracting frame reads state: $backtrack", backtrack > 0)
    }

    private fun TestScope.motion(): TraceLineMotion<Int> =
        TraceLineMotion(CoroutineScope(backgroundScope.coroutineContext + VirtualFrameClock(testScheduler)))

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
