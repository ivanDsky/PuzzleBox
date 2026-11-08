package com.ivandsky.puzzlebox.games.strandsui.board

import com.ivandsky.puzzlebox.games.strandsui.TraceEndKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StrandsTraceEndTimelineTest {

    @Test
    fun `a refused word is red, shakes for 230 ms, dissolves for the last 90 and is gone at 320`() {
        val start = TraceEndTimeline.look(TraceEndKind.Error, 0, reduceMotion = false)!!
        assertTrue(start.error)
        assertEquals(0f, start.shake, 1e-6f)
        assertEquals(1f, start.alpha, 1e-6f)

        // Eight stops over 230 ms: the second is -1 at 1/7 of the shake.
        assertEquals(-1f, TraceEndTimeline.look(TraceEndKind.Error, 230 / 7, false)!!.shake, 0.05f)
        assertEquals(1f, TraceEndTimeline.look(TraceEndKind.Error, 230 * 2 / 7, false)!!.shake, 0.05f)
        val shaking = TraceEndTimeline.look(TraceEndKind.Error, 100, false)!!
        assertEquals(1f, shaking.alpha, 1e-6f)
        assertEquals(1f, shaking.scale, 1e-6f)

        val dissolving = TraceEndTimeline.look(TraceEndKind.Error, 275, false)!!
        assertTrue(dissolving.error)
        assertEquals(0f, dissolving.shake, 1e-6f)
        assertTrue(dissolving.alpha in 0.01f..0.99f)
        assertTrue(dissolving.scale in 0.95f..0.999f)

        assertNull(TraceEndTimeline.look(TraceEndKind.Error, 320, false))
        assertEquals(320, TraceEndTimeline.totalMs(TraceEndKind.Error, false))
    }

    @Test
    fun `the shake never leaves the unit amplitude`() {
        for (ms in 0 until 320) {
            assertTrue(TraceEndTimeline.look(TraceEndKind.Error, ms, false)!!.shake in -1f..1f)
        }
    }

    @Test
    fun `a set-aside word holds neutral, then dissolves for 160 ms`() {
        for ((kind, hold) in listOf(TraceEndKind.Bonus to 60, TraceEndKind.Short to 200, TraceEndKind.Repeat to 260)) {
            val held = TraceEndTimeline.look(kind, hold - 1, reduceMotion = false)!!
            assertEquals(false, held.error)
            assertEquals(1f, held.alpha, 1e-6f)
            assertEquals(0f, held.shake, 0f)
            val fading = TraceEndTimeline.look(kind, hold + 80, false)!!
            assertTrue(fading.alpha < 1f)
            assertNull(TraceEndTimeline.look(kind, hold + 160, false))
            assertEquals(hold + 160, TraceEndTimeline.totalMs(kind, false))
        }
    }

    @Test
    fun `under reduce motion the colour holds with no shake and no dissolve, then clears`() {
        val red = TraceEndTimeline.look(TraceEndKind.Error, 599, reduceMotion = true)!!
        assertTrue(red.error)
        assertEquals(0f, red.shake, 0f)
        assertEquals(1f, red.alpha, 0f)
        assertEquals(1f, red.scale, 0f)
        assertNull(TraceEndTimeline.look(TraceEndKind.Error, 600, true))

        assertNotEquals(null, TraceEndTimeline.look(TraceEndKind.Bonus, 399, true))
        assertNull(TraceEndTimeline.look(TraceEndKind.Bonus, 400, true))
        assertEquals(300, TraceEndTimeline.totalMs(TraceEndKind.Short, true))
        assertEquals(300, TraceEndTimeline.totalMs(TraceEndKind.Repeat, true))
    }

    @Test
    fun `the tiles of a repeated theme word pulse to 1_1 at 40 percent of 400 ms and are back at 1 when it ends`() {
        assertEquals(1f, TraceEndTimeline.look(TraceEndKind.Repeat, 0, false, pulse = true)!!.scale, 1e-6f)
        assertEquals(1.1f, TraceEndTimeline.look(TraceEndKind.Repeat, 160, false, pulse = true)!!.scale, 1e-3f)
        assertEquals(1f, TraceEndTimeline.pulseScale(400), 1e-6f)
        assertEquals(1f, TraceEndTimeline.pulseScale(500), 0f)
        assertTrue(TraceEndTimeline.pulseScale(80) in 1.01f..1.09f)
    }

    @Test
    fun `without the pulse, or under reduce motion, the tiles do not scale in the hold`() {
        assertEquals(1f, TraceEndTimeline.look(TraceEndKind.Repeat, 160, false)!!.scale, 0f)
        assertEquals(1f, TraceEndTimeline.look(TraceEndKind.Repeat, 160, true, pulse = true)!!.scale, 0f)
    }
}
