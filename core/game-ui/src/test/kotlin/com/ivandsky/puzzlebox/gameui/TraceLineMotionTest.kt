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

    @Test
    fun `a forward tip rides the line's end toward its cell without a blend`() = runTest {
        val motion = motion()
        motion.step(listOf(0, 1), animate = false)

        // Arming a small lean moves nothing but the finger; a lean that grows follows 1:1,
        // with no tween between the finger and the end.
        assertEquals(1.25f, motion.step(listOf(0, 1), TraceTip(TipKind.Forward, 2, 0.25f), animate = true))
        assertEquals(1.6f, motion.step(listOf(0, 1), TraceTip(TipKind.Forward, 2, 0.6f), animate = true))
        assertEquals("the tip cell rides the line's end", listOf(0, 1, 2), motion.line)
    }

    @Test
    fun `a back tip pulls the end along the committed line`() = runTest {
        val motion = motion()
        motion.step(listOf(0, 1), animate = false)

        assertEquals(0.75f, motion.step(listOf(0, 1), TraceTip(TipKind.Back, 0, 0.25f), animate = true))
        assertEquals(0.5f, motion.step(listOf(0, 1), TraceTip(TipKind.Back, 0, 0.5f), animate = true))
        assertEquals("a back tip adds no cell", listOf(0, 1), motion.line)
    }

    @Test
    fun `a tip let go returns home over HomeDuration`() = runTest {
        val motion = motion()
        motion.step(listOf(0, 1), animate = false)
        motion.step(listOf(0, 1), TraceTip(TipKind.Forward, 2, 0.9f), animate = true)
        frames(MotionTokens.Trace.GrowDuration + 3 * FRAME_MS) { motion.step(listOf(0, 1), TraceTip(TipKind.Forward, 2, 0.9f), animate = true) }

        // The release frame starts the home tween but still draws the lean.
        assertEquals(1.9f, motion.step(listOf(0, 1), animate = true))
        val ends = frames(MotionTokens.Trace.HomeDuration + 3 * FRAME_MS) { motion.step(listOf(0, 1), animate = true) }

        ends.zipWithNext().forEach { (a, b) -> assertTrue("homes monotonically: $a then $b", b <= a) }
        assertTrue("still leaning one frame in: ${ends.first()}", ends.first() < 1.9f)
        assertEquals(1f, ends.last())
    }

    @Test
    fun `a lean appearing far from the tail grows in instead of snapping`() = runTest {
        val motion = motion()
        motion.step(listOf(0, 1), animate = false)

        assertEquals("the arming frame still draws the tail", 1f, motion.step(listOf(0, 1), TraceTip(TipKind.Forward, 2, 0.9f), animate = true))
        val ends = frames(MotionTokens.Trace.GrowDuration + 3 * FRAME_MS) { motion.step(listOf(0, 1), TraceTip(TipKind.Forward, 2, 0.9f), animate = true) }
        assertEquals(1.9f, ends.last())
    }

    @Test
    fun `a tip armed while a step blend runs extends from the blended end and reaches its lean`() = runTest {
        // A drag commits a cell and the finger keeps going, so the lean arms while the commit's
        // grow blend is pending or mid-flight (GrowDuration is six frames). The end must keep
        // climbing from where the blend has it, never snap back to the tail, never stall there.
        // Green on first run: the lean is part of the blend's target, so it is never swallowed.
        // The stall seen under Robolectric came from that test, which draws the board first at
        // its capture (see StrandsEditorDragRenderTest), not from this class.
        for (armAfterFrames in 0..8) {
            for (committed in listOf(listOf(0, 1, 2), listOf(0, 1, 2, 3, 4))) {
                val motion = motion()
                motion.step(listOf(0, 1), animate = false)
                motion.step(committed, animate = true)
                val lean = TraceTip(TipKind.Forward, committed.last() + 1, 0.9f)

                val before = frames(armAfterFrames * FRAME_MS) { motion.step(committed, animate = true) }
                val ends = before + frames(MotionTokens.Trace.GrowDuration + 5 * FRAME_MS) { motion.step(committed, lean, animate = true) }

                val label = "armed after $armAfterFrames frames on $committed: $ends"
                ends.zipWithNext().forEach { (a, b) -> assertTrue("never backwards, $label", b >= a - 1e-4f) }
                assertEquals("reaches the lean, $label", committed.lastIndex + 0.9f, ends.last(), 1e-4f)
            }
        }
    }

    @Test
    fun `reduced motion draws the tip at once, and home on lift`() = runTest {
        val motion = motion()
        motion.step(listOf(0, 1), animate = false)
        assertEquals(1.9f, motion.step(listOf(0, 1), TraceTip(TipKind.Forward, 2, 0.9f), animate = false))
        assertEquals(1f, motion.step(listOf(0, 1), animate = false))
    }

    @Test
    fun `a held drag draws the end at its target on the frame of every commit`() = runTest {
        // Ticket 02b: each commit restarted the grow blend, and a blend's first two frames draw
        // the old end, so a finger taking a cell a frame left the line standing at the start.
        val motion = motion()
        motion.step(listOf(0), animate = false)

        assertEquals(1.5f, motion.step(listOf(0, 1), TraceTip(TipKind.Forward, 2, 0.5f), animate = true, held = true))
        assertEquals(2.4f, motion.step(listOf(0, 1, 2), TraceTip(TipKind.Forward, 3, 0.4f), animate = true, held = true))
        assertEquals("the tip cell rides the line's end", listOf(0, 1, 2, 3), motion.line)
    }

    @Test
    fun `a held finger crossing the tail centre carries the end through it without homing`() = runTest {
        // Ticket 02b: the tip going null on the centre started a HomeDuration blend there, so
        // the end hung behind a finger that was already past.
        val motion = motion()
        motion.step(listOf(0, 1), animate = false)

        assertEquals(0.95f, motion.step(listOf(0, 1), TraceTip(TipKind.Back, 0, 0.05f), animate = true, held = true))
        assertEquals(1f, motion.step(listOf(0, 1), animate = true, held = true))
        assertEquals(1.05f, motion.step(listOf(0, 1), TraceTip(TipKind.Forward, 2, 0.05f), animate = true, held = true))
        assertEquals(1.1f, motion.step(listOf(0, 1), TraceTip(TipKind.Forward, 2, 0.1f), animate = true, held = true))
    }

    @Test
    fun `a held lean appearing far from the tail is drawn at once`() = runTest {
        // Ticket 02b: under the finger there is nothing to grow toward; the grow blend put
        // 100 ms between the finger and the end.
        val motion = motion()
        motion.step(listOf(0, 1), animate = false)

        assertEquals(1.9f, motion.step(listOf(0, 1), TraceTip(TipKind.Forward, 2, 0.9f), animate = true, held = true))
    }

    @Test
    fun `a held backtrack draws the shortened line at once`() = runTest {
        // Ticket 02b: the retract blend kept the removed cell drawn while the finger was
        // already back over the previous one.
        val back = motion()
        back.step(listOf(0, 1, 2), animate = false)
        assertEquals(0.8f, back.step(listOf(0, 1), TraceTip(TipKind.Back, 0, 0.2f), animate = true, held = true))
        assertEquals(listOf(0, 1), back.line)

        // Backtracked onto the near half of the removed cell: the end leans back at it.
        val lean = motion()
        lean.step(listOf(0, 1, 2), animate = false)
        assertEquals(1.49f, lean.step(listOf(0, 1), TraceTip(TipKind.Forward, 2, 0.49f), animate = true, held = true))
        assertEquals(listOf(0, 1, 2), lean.line)
    }

    @Test
    fun `a blend running when the finger takes hold is dropped for good`() = runTest {
        // Ticket 02b: a hint or undo blend still in flight when the drag starts must not
        // pull the end off the finger, nor resume when the finger lets go.
        val motion = motion()
        motion.step(listOf(0), animate = false)
        motion.step(listOf(0, 1, 2, 3), animate = true)
        frames(FRAME_MS) { motion.step(listOf(0, 1, 2, 3), animate = true) }

        assertEquals(3f, motion.step(listOf(0, 1, 2, 3), animate = true, held = true))
        assertEquals(listOf(3f), frames(FRAME_MS) { motion.step(listOf(0, 1, 2, 3), animate = true) })
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
