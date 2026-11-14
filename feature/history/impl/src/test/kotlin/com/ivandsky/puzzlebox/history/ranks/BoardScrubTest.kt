package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The hub's scrub on its own (user-check F10, F15): where a drag puts it, which board a release, a touch taken away and
 * a pick settle it on, and the spring it settles with. Distances are board widths, among four boards; frames come from
 * a virtual clock, 16 ms apart.
 */
class BoardScrubTest {

    private fun TestScope.scrubAt(position: Int) = BoardScrub(position, this + VirtualFrameClock(testScheduler))

    // The settle starts its clock on its first frame: after n frames it has played (n - 1) * 16 ms.
    private fun TestScope.frames(count: Int) {
        testScheduler.advanceTimeBy(count * FRAME_MS)
        testScheduler.runCurrent()
    }

    private fun BoardScrub.release(position: Int, velocity: Float = 0f) = release(position, velocity, FLING, COMMIT)

    private fun assertBetween(from: Int, to: Int, progress: Float, scrub: BoardScrub) {
        assertEquals("from", from, scrub.from)
        assertEquals("to", to, scrub.to)
        assertEquals("progress", progress, scrub.progress, TOLERANCE)
    }

    private fun assertAtRestOn(position: Int, scrub: BoardScrub) = assertBetween(position, position, 0f, scrub)

    @Test
    fun `a drag moves the scrub with the finger towards the next board and back`() = runTest {
        val scrub = scrubAt(1)

        assertEquals(0f, scrub.dragBy(-0.3f, COUNT))
        assertBetween(1, 2, 0.3f, scrub)

        scrub.dragBy(0.1f, COUNT)
        assertBetween(1, 2, 0.2f, scrub)
    }

    @Test
    fun `a drag back past where it began scrubs to the previous board`() = runTest {
        val scrub = scrubAt(1)

        scrub.dragBy(-0.2f, COUNT)
        scrub.dragBy(0.5f, COUNT)

        assertBetween(1, 0, 0.3f, scrub)
    }

    @Test
    fun `a drag scrubs no further than the neighbouring board and hands back the rest`() = runTest {
        val scrub = scrubAt(1)

        assertEquals(-0.5f, scrub.dragBy(-1.5f, COUNT), TOLERANCE)

        assertBetween(1, 2, 1f, scrub)
    }

    @Test
    fun `past the first and the last board the scrub stays and hands the whole drag back`() = runTest {
        val first = scrubAt(0)
        val last = scrubAt(COUNT - 1)

        assertEquals(0.2f, first.dragBy(0.2f, COUNT), TOLERANCE)
        assertEquals(-0.2f, last.dragBy(-0.2f, COUNT), TOLERANCE)

        assertAtRestOn(0, first)
        assertAtRestOn(COUNT - 1, last)
    }

    @Test
    fun `a release past the first board goes nowhere, even on a flick`() = runTest {
        val scrub = scrubAt(0)
        scrub.dragBy(0.4f, COUNT)

        assertEquals(0, scrub.release(position = 0, velocity = -FLING))

        testScheduler.advanceUntilIdle()
        assertAtRestOn(0, scrub)
    }

    @Test
    fun `a release at the commit point lands on the next board`() = runTest {
        val scrub = scrubAt(1)
        scrub.dragBy(-0.25f, COUNT)

        assertEquals(2, scrub.release(position = 1))

        frames(2)
        assertEquals(1, scrub.from)
        assertEquals(2, scrub.to)
        testScheduler.advanceUntilIdle()
        assertAtRestOn(2, scrub)
    }

    @Test
    fun `a rightward release at the commit point lands on the previous board`() = runTest {
        val scrub = scrubAt(1)
        scrub.dragBy(0.25f, COUNT)

        assertEquals(0, scrub.release(position = 1))

        testScheduler.advanceUntilIdle()
        assertAtRestOn(0, scrub)
    }

    @Test
    fun `a release short of the commit point returns`() = runTest {
        val scrub = scrubAt(1)
        scrub.dragBy(-0.24f, COUNT)

        assertEquals(1, scrub.release(position = 1))

        frames(2)
        assertEquals(1, scrub.from)
        assertEquals(2, scrub.to)
        testScheduler.advanceUntilIdle()
        assertAtRestOn(1, scrub)
    }

    @Test
    fun `a flick commits however short the drag`() = runTest {
        val scrub = scrubAt(1)
        scrub.dragBy(-0.02f, COUNT)

        assertEquals(2, scrub.release(position = 1, velocity = FLING))

        testScheduler.advanceUntilIdle()
        assertAtRestOn(2, scrub)
    }

    @Test
    fun `a release slower than a flick is decided by its distance`() = runTest {
        val scrub = scrubAt(1)
        scrub.dragBy(-0.02f, COUNT)

        assertEquals(1, scrub.release(position = 1, velocity = FLING - 0.01f))
    }

    @Test
    fun `a flick back the other way returns however far the drag`() = runTest {
        val scrub = scrubAt(1)
        scrub.dragBy(-0.6f, COUNT)

        assertEquals(1, scrub.release(position = 1, velocity = -FLING))

        testScheduler.advanceUntilIdle()
        assertAtRestOn(1, scrub)
    }

    @Test
    fun `a flick is further along than a slow release after the same time`() = runTest {
        val slow = scrubAt(1)
        val flicked = scrubAt(1)
        slow.dragBy(-0.25f, COUNT)
        flicked.dragBy(-0.25f, COUNT)

        slow.release(position = 1)
        flicked.release(position = 1, velocity = 2f)
        frames(5)

        assertTrue("slow ${slow.progress}, flicked ${flicked.progress}", flicked.progress > slow.progress + 0.05f)
    }

    @Test
    fun `a settle only ever moves towards its board and lands on it without a step`() = runTest {
        val scrub = scrubAt(1)
        scrub.dragBy(-0.25f, COUNT)
        scrub.release(position = 1, velocity = 2f)

        val shares = mutableListOf(scrub.share(2))
        while (scrub.to != scrub.from) {
            frames(1)
            shares += scrub.share(2)
        }

        shares.zipWithNext().forEachIndexed { frame, (before, after) ->
            assertTrue("frame $frame went back from $before to $after", after >= before)
            assertTrue("frame $frame stepped from $before to $after", after - before < 0.1f)
        }
        assertEquals(1f, shares.last())
    }

    @Test
    fun `a touch taken away returns the scrub however far the drag`() = runTest {
        val scrub = scrubAt(1)
        scrub.dragBy(-0.6f, COUNT)

        scrub.cancel()

        frames(5)
        assertEquals(2, scrub.to)
        assertTrue("progress ${scrub.progress}", scrub.progress < 0.6f)
        testScheduler.advanceUntilIdle()
        assertAtRestOn(1, scrub)
    }

    @Test
    fun `a touch taken away from a committed swipe lets it land`() = runTest {
        val scrub = scrubAt(1)
        scrub.dragBy(-0.3f, COUNT)
        scrub.release(position = 1)
        frames(4)
        scrub.dragBy(0.05f, COUNT)
        val caughtAt = scrub.progress

        scrub.cancel()

        frames(5)
        assertEquals(1, scrub.from)
        assertTrue("progress ${scrub.progress} after $caughtAt", scrub.progress > caughtAt)
        testScheduler.advanceUntilIdle()
        assertAtRestOn(2, scrub)
    }

    // Caught early and nudged back, the scrub is short of the commit point again; the swipe had committed all the same.
    @Test
    fun `a committed swipe caught on its way stays committed`() = runTest {
        val scrub = scrubAt(1)
        scrub.dragBy(-0.3f, COUNT)
        scrub.release(position = 1)
        frames(2)
        scrub.moveTo(2, instant = false)

        scrub.dragBy(0.1f, COUNT)
        assertTrue("progress ${scrub.progress}", scrub.progress < COMMIT)

        assertEquals(2, scrub.release(position = 2))
        testScheduler.advanceUntilIdle()
        assertAtRestOn(2, scrub)
    }

    // Caught late, the scrub is still past the commit point after the finger has taken it the commit distance back.
    @Test
    fun `a committed swipe the finger takes back by the commit distance returns`() = runTest {
        val scrub = scrubAt(1)
        scrub.dragBy(-0.3f, COUNT)
        scrub.release(position = 1)
        frames(11)
        scrub.moveTo(2, instant = false)

        scrub.dragBy(0.3f, COUNT)
        assertTrue("progress ${scrub.progress}", scrub.progress > COMMIT)

        assertEquals(1, scrub.release(position = 2))
        testScheduler.advanceUntilIdle()
        assertAtRestOn(1, scrub)
    }

    @Test
    fun `a pick settles on the picked board, across the boards between`() = runTest {
        val scrub = scrubAt(0)

        scrub.moveTo(3, instant = false)

        frames(7)
        assertEquals(0, scrub.from)
        assertEquals(3, scrub.to)
        assertTrue("progress ${scrub.progress}", scrub.progress > 0f && scrub.progress < 1f)
        testScheduler.advanceUntilIdle()
        assertAtRestOn(3, scrub)
    }

    @Test
    fun `a pick under reduced motion lands at once`() = runTest {
        val scrub = scrubAt(0)

        scrub.moveTo(3, instant = true)

        assertAtRestOn(3, scrub)
    }

    // What is left of the way on the settle spring (critically damped, stiffness 200) is (1 + wt) * e^(-wt), w = sqrt(200):
    // 0.6065 of it after 96 ms and 0.2080 after 208 ms.
    @Test
    fun `a pick and a released swipe settle on the same soft spring`() = runTest {
        val picked = scrubAt(0)
        val swiped = scrubAt(0)

        picked.moveTo(1, instant = false)
        swiped.dragBy(-0.25f, COUNT)
        swiped.release(position = 0)

        frames(7)
        assertEquals(1f - 0.6065f, picked.progress, SPRING_TOLERANCE)
        assertEquals(1f - 0.75f * 0.6065f, swiped.progress, SPRING_TOLERANCE)
        frames(7)
        assertEquals(1f - 0.2080f, picked.progress, SPRING_TOLERANCE)
        assertEquals(1f - 0.75f * 0.2080f, swiped.progress, SPRING_TOLERANCE)
    }

    @Test
    fun `a pick while a finger holds the scrub waits for the finger to let go`() = runTest {
        val scrub = scrubAt(1)
        scrub.dragBy(-0.1f, COUNT)

        scrub.moveTo(3, instant = false)
        frames(5)

        assertBetween(1, 2, 0.1f, scrub)
        scrub.cancel()
        testScheduler.advanceUntilIdle()
        assertAtRestOn(3, scrub)
    }

    @Test
    fun `a pick of the board a swipe is leaving turns it round`() = runTest {
        val scrub = scrubAt(1)
        scrub.dragBy(-0.3f, COUNT)
        scrub.release(position = 1)
        frames(4)
        val turnedAt = scrub.progress

        scrub.moveTo(1, instant = false)

        frames(5)
        assertEquals(2, scrub.to)
        assertTrue("progress ${scrub.progress} after $turnedAt", scrub.progress < turnedAt)
        testScheduler.advanceUntilIdle()
        assertAtRestOn(1, scrub)
    }

    @Test
    fun `a pick early in a move to another board starts from the board being left`() = runTest {
        val scrub = scrubAt(0)
        scrub.moveTo(1, instant = false)
        frames(7)

        scrub.moveTo(3, instant = false)

        assertBetween(0, 3, 0f, scrub)
        testScheduler.advanceUntilIdle()
        assertAtRestOn(3, scrub)
    }

    @Test
    fun `a pick late in a move to another board starts from the board it was arriving at`() = runTest {
        val scrub = scrubAt(0)
        scrub.moveTo(1, instant = false)
        frames(11)

        scrub.moveTo(3, instant = false)

        assertBetween(1, 3, 0f, scrub)
        testScheduler.advanceUntilIdle()
        assertAtRestOn(3, scrub)
    }

    @Test
    fun `a finger catching a pick across several boards early scrubs from the board being left`() = runTest {
        val scrub = scrubAt(0)
        scrub.moveTo(3, instant = false)
        frames(7)

        scrub.dragBy(-0.1f, COUNT)

        assertBetween(0, 1, 0.1f, scrub)
    }

    @Test
    fun `a finger catching a pick across several boards late scrubs from the picked board`() = runTest {
        val scrub = scrubAt(0)
        scrub.moveTo(3, instant = false)
        frames(11)

        scrub.dragBy(0.1f, COUNT)

        assertBetween(3, 2, 0.1f, scrub)
    }

    private class VirtualFrameClock(private val scheduler: TestCoroutineScheduler) : MonotonicFrameClock {
        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            delay(FRAME_MS)
            return onFrame(scheduler.currentTime * 1_000_000)
        }
    }

    private companion object {
        const val COUNT = 4
        const val COMMIT = 0.25f
        const val FLING = 0.9f
        const val FRAME_MS = 16L
        const val TOLERANCE = 1e-5f
        const val SPRING_TOLERANCE = 0.001f
    }
}
