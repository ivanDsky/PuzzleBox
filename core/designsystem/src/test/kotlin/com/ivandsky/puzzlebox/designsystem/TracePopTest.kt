package com.ivandsky.puzzlebox.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The trace pop, frame by frame on a virtual clock: 1 → PopScale → 1 within PopDuration. */
class TracePopTest {

    @Test
    fun `the animated pop rises for the rise fraction, peaks at PopScale, and settles by PopDuration`() = runTest {
        val clock = VirtualFrameClock(testScheduler)
        val pop = Animatable(1f)
        val samples = mutableListOf<Pair<Long, Float>>()
        val start = testScheduler.currentTime
        val job = launch(clock) { pop.tracePop() }
        while (job.isActive) {
            testScheduler.advanceTimeBy(FRAME_MS)
            testScheduler.runCurrent()
            samples += (testScheduler.currentTime - start) to pop.value
        }

        val rise = (MotionTokens.Trace.PopDuration * MotionTokens.Trace.PopRiseFraction).toLong()
        val peakAt = samples.first { it.second == MotionTokens.Trace.PopScale }.first
        // One frame starts the clock, the rise runs, the settle starts on the next frame.
        assertEquals(FRAME_MS + rise, peakAt)
        samples.zipWithNext().forEach { (a, b) ->
            if (b.first <= peakAt) assertTrue("rising at ${b.first}ms", b.second >= a.second) else assertTrue("settling at ${b.first}ms", b.second <= a.second)
        }
        val (endAt, end) = samples.last()
        assertEquals(1f, end)
        assertTrue("settled at ${endAt}ms", endAt <= MotionTokens.Trace.PopDuration + 3 * FRAME_MS)
    }

    @Test
    fun `the keyframed pop is 1 at rest, PopScale at the rise, and clamps outside 0 to 1`() {
        assertEquals(1f, tracePopScale(0f))
        assertEquals(MotionTokens.Trace.PopScale, tracePopScale(MotionTokens.Trace.PopRiseFraction))
        assertEquals(1f, tracePopScale(1f))
        assertEquals(1f, tracePopScale(-0.5f))
        assertEquals(1f, tracePopScale(2f))
        val half = tracePopScale(MotionTokens.Trace.PopRiseFraction / 2)
        assertTrue(half > 1f && half < MotionTokens.Trace.PopScale)
        // EasingOut front-loads the rise: half the time is well past half the height.
        assertTrue(half - 1f > (MotionTokens.Trace.PopScale - 1f) / 2)
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
