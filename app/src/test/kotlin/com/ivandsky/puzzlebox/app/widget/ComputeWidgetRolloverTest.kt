package com.ivandsky.puzzlebox.app.widget

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ComputeWidgetRolloverTest {

    private val compute = ComputeWidgetRollover()
    private val la = ZoneId.of("America/Los_Angeles")
    private val tokyo = ZoneId.of("Asia/Tokyo")

    private fun at(zone: ZoneId, y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0): Instant =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, zone).toInstant()

    @Test
    fun `one zone gives its next midnight`() {
        val now = at(la, 2026, 11, 20, 15, 30)

        assertEquals(at(la, 2026, 11, 21), compute(now, listOf(la)))
    }

    @Test
    fun `two zones pick the earlier midnight`() {
        val now = at(la, 2026, 11, 20, 3) // 20:00 in Tokyo: its midnight is 4 hours off, LA's 21

        assertEquals(at(tokyo, 2026, 11, 21), compute(now, listOf(la, tokyo)))
        assertEquals(at(tokyo, 2026, 11, 21), compute(now, listOf(tokyo, la)))
    }

    @Test
    fun `exactly at midnight gives the next day's`() {
        val midnight = at(la, 2026, 11, 21)

        assertEquals(at(la, 2026, 11, 22), compute(midnight, listOf(la)))
    }

    @Test
    fun `a DST day is not 24 hours`() {
        val now = at(la, 2026, 3, 8, 1) // spring forward at 02:00: the 8th has 23 hours

        val rollover = compute(now, listOf(la))

        assertEquals(at(la, 2026, 3, 9), rollover)
        assertEquals(22L, Duration.between(now, rollover).toHours())
    }

    @Test
    fun `an empty zone set is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { compute(Instant.EPOCH, emptyList()) }
    }
}
