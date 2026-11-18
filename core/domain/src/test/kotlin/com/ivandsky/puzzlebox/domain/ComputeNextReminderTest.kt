package com.ivandsky.puzzlebox.domain

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class ComputeNextReminderTest {

    private val next = ComputeNextReminder()
    private val newYork = ZoneId.of("America/New_York")
    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val evening = LocalTime.of(19, 0)

    private fun at(zone: ZoneId, now: String, time: LocalTime = evening): Instant = next(Instant.parse(now), zone, time)

    @Test
    fun `before the time the reminder is today's`() {
        assertEquals(Instant.parse("2026-11-10T19:00:00Z"), at(ZoneId.of("UTC"), "2026-11-10T08:00:00Z"))
    }

    @Test
    fun `at the time exactly the reminder is tomorrow's, strictly after now`() {
        assertEquals(Instant.parse("2026-11-11T19:00:00Z"), at(ZoneId.of("UTC"), "2026-11-10T19:00:00Z"))
    }

    @Test
    fun `after the time the reminder is tomorrow's`() {
        assertEquals(Instant.parse("2026-11-11T19:00:00Z"), at(ZoneId.of("UTC"), "2026-11-10T21:30:00Z"))
    }

    @Test
    fun `midnight is the next midnight, also when asked a second after it`() {
        val utc = ZoneId.of("UTC")

        assertEquals(Instant.parse("2026-11-11T00:00:00Z"), at(utc, "2026-11-10T23:59:59Z", LocalTime.MIDNIGHT))
        assertEquals(Instant.parse("2026-11-12T00:00:00Z"), at(utc, "2026-11-11T00:00:00Z", LocalTime.MIDNIGHT))
    }

    @Test
    fun `the wall clock is read in the given zone`() {
        // 2026-11-10T08:00Z is 03:00 in New York and 17:00 in Tokyo: 19:00 is still ahead in both, at different instants.
        assertEquals(Instant.parse("2026-11-11T00:00:00Z"), at(newYork, "2026-11-10T08:00:00Z"))
        assertEquals(Instant.parse("2026-11-10T10:00:00Z"), at(tokyo, "2026-11-10T08:00:00Z"))
    }

    @Test
    fun `a time inside the spring-forward gap moves forward`() {
        // New York 2026-03-08: 02:00 jumps to 03:00, so 02:30 does not exist and lands at 03:30 EDT (07:30Z).
        assertEquals(
            Instant.parse("2026-03-08T07:30:00Z"),
            at(newYork, "2026-03-08T00:00:00Z", LocalTime.of(2, 30)),
        )
    }

    @Test
    fun `a time in the fall-back overlap fires once, at its first occurrence`() {
        // New York 2026-11-01: 01:30 happens at 05:30Z (EDT) and again at 06:30Z (EST).
        val half = LocalTime.of(1, 30)

        assertEquals(Instant.parse("2026-11-01T05:30:00Z"), at(newYork, "2026-11-01T05:00:00Z", half))
        // Past the first 01:30 and before the second: the next one is tomorrow's, not the repeat.
        assertEquals(Instant.parse("2026-11-02T06:30:00Z"), at(newYork, "2026-11-01T05:45:00Z", half))
        assertEquals(Instant.parse("2026-11-02T06:30:00Z"), at(newYork, "2026-11-01T06:15:00Z", half))
    }
}
