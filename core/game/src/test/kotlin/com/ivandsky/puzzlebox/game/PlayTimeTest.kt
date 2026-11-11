package com.ivandsky.puzzlebox.game

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayTimeTest {

    private val start: Instant = Instant.ofEpochMilli(1_000_000L)

    @Test
    fun `no open window is the banked time`() {
        assertEquals(42_000L, playTimeMs(bankedMs = 42_000L, runningSince = null, now = start))
    }

    @Test
    fun `window of zero adds nothing`() {
        assertEquals(42_000L, playTimeMs(bankedMs = 42_000L, runningSince = start, now = start))
    }

    @Test
    fun `normal window adds its length to the banked time`() {
        val now = start.plusMillis(5_500L)
        assertEquals(47_500L, playTimeMs(bankedMs = 42_000L, runningSince = start, now = now))
    }

    @Test
    fun `now before runningSince counts the window as zero`() {
        val now = start.minusMillis(3_000L)
        assertEquals(42_000L, playTimeMs(bankedMs = 42_000L, runningSince = start, now = now))
    }

    @Test
    fun `negative banked value never yields a negative result`() {
        assertEquals(0L, playTimeMs(bankedMs = -7_000L, runningSince = null, now = start))
        assertEquals(5_000L, playTimeMs(bankedMs = -7_000L, runningSince = start, now = start.plusMillis(5_000L)))
    }
}
