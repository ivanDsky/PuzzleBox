package com.ivandsky.puzzlebox.data.cloud

import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The two pure failure decisions: what a failed call leaves behind ([SyncBackoff]) and the daily write cap ([WriteCap]). */
class SyncBackoffTest {

    private val minute = 60_000L

    @Test
    fun `offline with the network down leads to Offline and waits for a trigger whatever the attempt`() {
        for (attempt in listOf(1, 2, 9)) {
            val pause = checkNotNull(SyncBackoff.after(CloudResult.Offline, attempt, lastAtMs = 7L, online = false))
            assertEquals(CloudSyncState.Offline(7L), pause.state)
            assertNull(pause.retryAfterMs)
        }
    }

    @Test
    fun `offline with the network up is a timeout and retries on the doubling delay capped at 30 minutes`() {
        val minutes = listOf(1L, 2L, 4L, 8L, 16L, 30L, 30L)
        minutes.forEachIndexed { index, expected ->
            val pause = checkNotNull(SyncBackoff.after(CloudResult.Offline, index + 1, lastAtMs = 7L, online = true))
            assertEquals(CloudSyncState.Offline(7L), pause.state)
            assertEquals("attempt ${index + 1}", expected * minute, pause.retryAfterMs)
        }
    }

    @Test
    fun `offline keeps a missing last time as null`() {
        assertEquals(CloudSyncState.Offline(null), SyncBackoff.after(CloudResult.Offline, 1, null, online = false)?.state)
    }

    @Test
    fun `exhausted and denied pause with the reason and the doubling delay capped at 30 minutes`() {
        val minutes = listOf(1L, 2L, 4L, 8L, 16L, 30L, 30L, 30L)
        for ((reason, result) in listOf(PauseReason.Exhausted to CloudResult.Exhausted, PauseReason.Denied to CloudResult.Denied)) {
            minutes.forEachIndexed { index, expected ->
                val pause = checkNotNull(SyncBackoff.after(result, attempt = index + 1, lastAtMs = 5L, online = true))
                assertEquals(CloudSyncState.Paused(reason, 5L), pause.state)
                assertEquals("attempt ${index + 1}", expected * minute, pause.retryAfterMs)
            }
        }
    }

    @Test
    fun `a huge attempt count stays at the cap without overflowing`() {
        for (attempt in listOf(40, 64, 65, 1_000, Int.MAX_VALUE)) {
            assertEquals(30 * minute, SyncBackoff.after(CloudResult.Denied, attempt, null, online = true)?.retryAfterMs)
        }
    }

    @Test
    fun `an attempt below one counts as the first`() {
        assertEquals(minute, SyncBackoff.after(CloudResult.Exhausted, 0, null, online = true)?.retryAfterMs)
    }

    @Test
    fun `ok and wrong account decide nothing`() {
        assertNull(SyncBackoff.after(CloudResult.Ok(Unit), 1, 1L, online = true))
        assertNull(SyncBackoff.after(CloudResult.WrongAccount, 1, 1L, online = true))
    }

    @Test
    fun `the write cap pauses until the given delay without a backoff`() {
        val pause = SyncBackoff.capped(lastAtMs = 3L, retryAfterMs = 12_345L)

        assertEquals(CloudSyncState.Paused(PauseReason.WriteCap, 3L), pause.state)
        assertEquals(12_345L, pause.retryAfterMs)
    }

    @Test
    fun `the write cap leaves room for 500 writes a day`() {
        assertEquals(500, WriteCap.DAILY_LIMIT)
        assertEquals(500, WriteCap.room(usedToday = 0))
        assertEquals(1, WriteCap.room(499))
        assertEquals(0, WriteCap.room(500))
        assertEquals(0, WriteCap.room(10_000))
    }

    @Test
    fun `the write cap day is the calendar date in the clock zone`() {
        val late = 1_700_000_000_000L
        val utc = WriteCap.day(late, ZoneOffset.UTC)

        assertEquals("2023-11-14", utc)
        assertEquals("2023-11-15", WriteCap.day(late, ZoneOffset.ofHours(10)))
        assertEquals(utc, WriteCap.day(late + 60_000L, ZoneOffset.UTC))
    }

    @Test
    fun `the time to the next day ends exactly at the day's end`() {
        val late = 1_700_000_000_000L // 2023-11-14T22:13:20Z
        val toMidnight = WriteCap.msUntilNextDay(late, ZoneOffset.UTC)

        assertEquals(6_400_000L, toMidnight)
        assertEquals("2023-11-15", WriteCap.day(late + toMidnight, ZoneOffset.UTC))
        assertEquals("2023-11-14", WriteCap.day(late + toMidnight - 1, ZoneOffset.UTC))
        assertEquals(24 * 60 * minute, WriteCap.msUntilNextDay(late + toMidnight, ZoneOffset.UTC))
    }
}
