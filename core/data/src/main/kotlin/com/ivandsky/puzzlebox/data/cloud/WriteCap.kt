package com.ivandsky.puzzlebox.data.cloud

import java.time.Instant
import java.time.ZoneId

/** The pure daily write cap (ADR-0029 decision 4, A12): a client-side bound on a bug loop, counted per account and calendar day. */
internal object WriteCap {
    const val DAILY_LIMIT = 500

    /** How many more writes today may send, given [usedToday] writes already counted. */
    fun room(usedToday: Int): Int = (DAILY_LIMIT - usedToday).coerceAtLeast(0)

    /** The counter's day key for [nowMs] in [zone]: the calendar date, `yyyy-MM-dd`. */
    fun day(nowMs: Long, zone: ZoneId): String = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().toString()

    /** Milliseconds from [nowMs] to the start of the next calendar day in [zone]; always above zero. */
    fun msUntilNextDay(nowMs: Long, zone: ZoneId): Long {
        val nextDay = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone)
        return nextDay.toInstant().toEpochMilli() - nowMs
    }
}
