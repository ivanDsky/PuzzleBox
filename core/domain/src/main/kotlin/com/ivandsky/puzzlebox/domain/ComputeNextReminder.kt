package com.ivandsky.puzzlebox.domain

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The first instant strictly after `now` whose wall-clock time in `zone` is `at`: today's `at` while it is still ahead,
 * else tomorrow's. The zone's rules settle a day with no such time (a spring-forward gap moves it forward) and one with
 * two (a fall-back overlap takes the earlier, so once that has passed the next reminder is tomorrow's: it fires once).
 */
class ComputeNextReminder {

    operator fun invoke(now: Instant, zone: ZoneId, at: LocalTime): Instant {
        val today = now.atZone(zone).toLocalDate()
        val first = ZonedDateTime.of(today, at, zone).toInstant()
        return if (first.isAfter(now)) first else ZonedDateTime.of(today.plusDays(1), at, zone).toInstant()
    }
}
