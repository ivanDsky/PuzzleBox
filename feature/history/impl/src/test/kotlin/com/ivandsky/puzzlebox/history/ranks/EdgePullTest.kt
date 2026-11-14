package com.ivandsky.puzzlebox.history.ranks

import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.plus
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The pull past the hub's first or last board on its own (user-check F15, F17): how far the content leans for it, what
 * a drag back inward does, and how it settles. Distances are px; frames come from a virtual clock, 16 ms apart.
 */
class EdgePullTest {

    private fun TestScope.edgePull() = EdgePull(this + VirtualFrameClock(testScheduler))

    // On the first board: the boards take none of a drag rightwards, past the end, and all of one back inward.
    private fun EdgePull.dragAtEdge(dx: Float) = dragBy(dx) { rest -> if (rest > 0f) rest else 0f }

    // The settle starts its clock on its first frame: after n frames it has played (n - 1) * 16 ms.
    private fun TestScope.frames(count: Int) {
        testScheduler.advanceTimeBy(count * FRAME_MS)
        testScheduler.runCurrent()
    }

    @Test
    fun `a pull past the edge leans the content a fifth of the way`() = runTest {
        val pull = edgePull()

        pull.dragAtEdge(60f)
        pull.dragAtEdge(40f)

        assertEquals(20f, pull.lean, TOLERANCE)
    }

    @Test
    fun `a drag back inward is taken from the pull before it moves the boards`() = runTest {
        val pull = edgePull()
        val scrubbed = mutableListOf<Float>()
        pull.dragAtEdge(100f)

        pull.dragBy(-30f) { rest -> scrubbed += rest; 0f }
        assertEquals(14f, pull.lean, TOLERANCE)

        pull.dragBy(-100f) { rest -> scrubbed += rest; 0f }
        assertEquals(0f, pull.lean, TOLERANCE)
        assertEquals(listOf(0f, -30f), scrubbed)
    }

    @Test
    fun `past the last board a drag back inward is taken from the pull the same way`() = runTest {
        val pull = edgePull()
        val scrubbed = mutableListOf<Float>()
        pull.dragBy(-100f) { rest -> rest }

        pull.dragBy(130f) { rest -> scrubbed += rest; 0f }

        assertEquals(0f, pull.lean, TOLERANCE)
        assertEquals(listOf(30f), scrubbed)
    }

    // What is left of the way on the settle spring (critically damped, stiffness 200) is (1 + wt) * e^(-wt),
    // w = sqrt(200): 0.6065 of it after 96 ms and 0.2080 after 208 ms.
    @Test
    fun `a release springs the lean back to nothing on the settle spring`() = runTest {
        val pull = edgePull()
        pull.dragAtEdge(100f)

        pull.release()

        frames(7)
        assertEquals(20f * 0.6065f, pull.lean, SPRING_TOLERANCE)
        frames(7)
        assertEquals(20f * 0.2080f, pull.lean, SPRING_TOLERANCE)
        testScheduler.advanceUntilIdle()
        assertEquals(0f, pull.lean, 0f)
    }

    @Test
    fun `a new drag during the settle picks the pull up where it is`() = runTest {
        val pull = edgePull()
        pull.dragAtEdge(100f)
        pull.release()
        frames(7)
        val caughtAt = pull.lean

        pull.dragAtEdge(10f)

        assertEquals(caughtAt + 2f, pull.lean, TOLERANCE)
        testScheduler.advanceUntilIdle()
        assertEquals(caughtAt + 2f, pull.lean, TOLERANCE)
    }

    private class VirtualFrameClock(private val scheduler: TestCoroutineScheduler) : MonotonicFrameClock {
        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            delay(FRAME_MS)
            return onFrame(scheduler.currentTime * 1_000_000)
        }
    }

    private companion object {
        const val FRAME_MS = 16L
        const val TOLERANCE = 1e-4f
        const val SPRING_TOLERANCE = 0.02f
    }
}
