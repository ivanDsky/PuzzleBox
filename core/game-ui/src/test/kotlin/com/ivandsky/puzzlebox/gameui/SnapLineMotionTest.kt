package com.ivandsky.puzzlebox.gameui

import androidx.compose.runtime.MonotonicFrameClock
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
 * The Snap trace line, frame by frame on a virtual clock. Cells are ints on one row, so a
 * line [0, 1, 2] ends at index 2 and nothing past the last cell is ever drawn.
 */
class SnapLineMotionTest {

    @Test
    fun `cells joined in one event grow in over the snap grow and settle one frame later`() = runTest {
        val motion = motion()
        assertEquals(0f, motion.step(listOf(0), animate = true))

        // The tween's coroutine has not run yet: the first frame still shows the old end.
        assertEquals(0f, motion.step(listOf(0, 1, 2), animate = true))
        val ends = frames(MotionTokens.Trace.SnapGrowDuration + 3 * FRAME_MS) { motion.step(listOf(0, 1, 2), animate = true) }

        ends.zipWithNext().forEach { (a, b) -> assertTrue("grows monotonically: $a then $b", b >= a) }
        val mid = ends[(MotionTokens.Trace.SnapGrowDuration / 2 / FRAME_MS).toInt()]
        assertTrue("mid-grow end $mid", mid > 1f && mid < 2f)
        assertTrue("never past the last cell", ends.all { it <= 2f })
        assertEquals("settled one frame past the grow", 2f, ends[((MotionTokens.Trace.SnapGrowDuration + FRAME_MS) / FRAME_MS).toInt()])
    }

    @Test
    fun `a backtrack retracts over the snap retract and settles one frame later`() = runTest {
        val motion = motion()
        motion.step(listOf(0, 1, 2), animate = false)

        assertEquals(2f, motion.step(listOf(0, 1), animate = true))
        assertEquals("the end runs back along the line just left", listOf(0, 1, 2), motion.line)
        val samples = frames(MotionTokens.Trace.SnapRetractDuration + 3 * FRAME_MS) { motion.step(listOf(0, 1), animate = true) to motion.line }
        val ends = samples.map { it.first }

        ends.zipWithNext().forEach { (a, b) -> assertTrue("retracts monotonically: $a then $b", b <= a) }
        assertTrue("still moving one frame in: ${ends.first()}", ends.first() > 1f)
        assertTrue("never under the last cell", ends.all { it >= 1f })
        val settled = ((MotionTokens.Trace.SnapRetractDuration + FRAME_MS) / FRAME_MS).toInt()
        assertEquals("settled one frame past the retract", 1f, ends[settled])
        assertEquals("the line is the path once settled", listOf(0, 1), samples[settled].second)
        assertTrue("the old line holds while moving", samples.take(settled - 1).all { it.second == listOf(0, 1, 2) })
    }

    @Test
    fun `a change under the blend minimum is drawn at once and stops the blend`() = runTest {
        val motion = motion()
        val grown = (0..4).toList()
        motion.step(listOf(0), animate = true)
        motion.step(grown, animate = true)
        val drawn = frames(2 * FRAME_MS) { motion.step(grown, animate = true) }.last()
        assertEquals("end mid-grow, 0.045 short of cell 3", 2.955f, drawn, 1e-3f)

        // Backtracking to cell 3 from there is a 0.045 change: the end jumps and the blend stops.
        val back = listOf(0, 1, 2, 3)
        assertEquals(3f, motion.step(back, animate = true))
        assertEquals(back, motion.line)
        assertTrue(frames(5 * FRAME_MS) { motion.step(back, animate = true) }.all { it == 3f })
    }

    @Test
    fun `a change during a blend restarts from where the end is drawn now`() = runTest {
        val motion = motion()
        val grown = (0..9).toList()
        motion.step(listOf(0), animate = true)
        motion.step(grown, animate = true)
        frames(3 * FRAME_MS) { motion.step(grown, animate = true) }
        val drawn = motion.step(grown, animate = true)
        assertTrue("part way $drawn", drawn > 0f && drawn < 9f)

        val longer = (0..10).toList()
        assertEquals("no jump on the event frame", drawn, motion.step(longer, animate = true))
        val ends = frames(MotionTokens.Trace.SnapGrowDuration + 3 * FRAME_MS) { motion.step(longer, animate = true) }
        ends.zipWithNext().forEach { (a, b) -> assertTrue("grows monotonically: $a then $b", b >= a) }
        assertEquals(10f, ends.last())
    }

    @Test
    fun `reduced motion draws the target at once`() = runTest {
        val motion = motion()
        motion.step(listOf(0), animate = false)
        assertEquals(2f, motion.step(listOf(0, 1, 2), animate = false))
        assertEquals(1f, motion.step(listOf(0, 1), animate = false))
    }

    @Test
    fun `an empty path clears the line and a running blend does not bleed into the next path`() = runTest {
        val motion = motion()
        motion.step(listOf(0), animate = true)
        motion.step(listOf(0, 1, 2), animate = true)
        frames(2 * FRAME_MS) { motion.step(listOf(0, 1, 2), animate = true) }

        assertEquals(0f, motion.step(emptyList(), animate = true))
        assertEquals(0f, motion.step(listOf(5), animate = true))
        val ends = frames(MotionTokens.Trace.SnapGrowDuration) { motion.step(listOf(5), animate = true) }
        assertTrue(ends.all { it == 0f })
    }

    @Test
    fun `no head state is exposed, the head is always at rest`() {
        val names = SnapLineMotion::class.java.methods.map { it.name.lowercase() }
        assertTrue(names.none { "head" in it || "pop" in it || "scale" in it || "tip" in it })
    }

    private fun TestScope.motion(): SnapLineMotion<Int> =
        SnapLineMotion(CoroutineScope(backgroundScope.coroutineContext + VirtualFrameClock(testScheduler)))

    /** Runs [frame] once per virtual frame for [ms]; the first sample is the frame after the event. */
    private fun <T> TestScope.frames(ms: Number, frame: () -> T): List<T> = buildList {
        repeat((ms.toLong() / FRAME_MS).toInt()) {
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
