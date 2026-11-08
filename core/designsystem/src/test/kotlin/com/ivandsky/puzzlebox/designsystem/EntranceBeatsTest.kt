package com.ivandsky.puzzlebox.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

/** The Results entrance: one beat list, the stagger inside a beat, and the count-up maths. */
class EntranceBeatsTest {

    @Test
    fun `beat delays are one list`() {
        assertEquals(listOf(0, 120, 520, 640, 840), ResultBeats.DelaysMs)
        assertEquals(0, beatDelayMs(ResultBeats.Glyph))
        assertEquals(120, beatDelayMs(ResultBeats.Time))
        assertEquals(520, beatDelayMs(ResultBeats.Supporting))
        assertEquals(640, beatDelayMs(ResultBeats.Blocks))
        assertEquals(840, beatDelayMs(ResultBeats.Actions))
    }

    @Test
    fun `blocks stagger 40 ms apart up to the cap`() {
        assertEquals(640, beatDelayMs(ResultBeats.Blocks, index = 0))
        assertEquals(680, beatDelayMs(ResultBeats.Blocks, index = 1))
        assertEquals(640 + 5 * 40, beatDelayMs(ResultBeats.Blocks, index = 5))
        assertEquals(640 + 5 * 40, beatDelayMs(ResultBeats.Blocks, index = 9))
    }

    @Test
    fun `reduced motion has no delay`() {
        for (beat in ResultBeats.DelaysMs.indices) {
            assertEquals(0, beatDelayMs(beat, index = 3, reduced = true))
        }
    }

    @Test
    fun `count-up runs the shared count-up duration`() {
        assertEquals(MotionTokens.CountUpDuration, COUNT_UP_DURATION_MS)
        assertEquals(400, COUNT_UP_DURATION_MS)
    }

    @Test
    fun `count-up value follows progress and lands on the target`() {
        assertEquals(0L, countUpValue(target = 3_723_000L, progress = 0f))
        assertEquals(1_000L, countUpValue(target = 2_000L, progress = 0.5f))
        assertEquals(3_723_000L, countUpValue(target = 3_723_000L, progress = 1f))
    }
}
