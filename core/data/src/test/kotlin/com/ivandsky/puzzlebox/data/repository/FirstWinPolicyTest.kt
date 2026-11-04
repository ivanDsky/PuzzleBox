package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.testing.game.TestGames.Ref
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure A4/A5 decision (A17): first win is the record; replays never become it. */
class FirstWinPolicyTest {

    private val policy = FirstWinPolicy()
    private val day = LocalDate.of(2026, 10, 31)
    private val refKey = "zip/2026-10-31/592"

    private fun emitted(timeMs: Long, hints: Int) = CompletionRecord(
        puzzleRef = Ref(GameId("zip"), day, 592),
        time = timeMs,
        hints = hints,
        stats = RecordStats(schemaVersion = 1, blob = """{"v":1,"mistakes":3}"""),
    )

    @Test
    fun `first completion of a ref decides record`() {
        val decision = policy.decide(hasRecord = false, nowMs = 7_000L)
        assertEquals(FirstWinPolicy.Decision.Record(7_000L), decision)
    }

    @Test
    fun `later completion of the same ref decides replay`() {
        val decision = policy.decide(hasRecord = true, nowMs = 67_000L)
        assertEquals(FirstWinPolicy.Decision.Replay(67_000L), decision)
    }

    @Test
    fun `record stamp carries the first-win instant and passes A3 inputs through`() {
        val row = policy.stamp(emitted(timeMs = 95_000L, hints = 2), refKey, FirstWinPolicy.Decision.Record(7_000L))

        assertTrue(row.isRecord)
        assertEquals(refKey, row.puzzleRef)
        assertEquals(95_000L, row.timeMs)
        assertEquals(2, row.hints)
        assertEquals(7_000L, row.completedAtMs)
        assertEquals(7_000L, row.firstCompletedAtMs)
        assertEquals(1, row.statsVersion)
        assertEquals("""{"v":1,"mistakes":3}""", row.statsBlob)
    }

    @Test
    fun `replay stamp never claims the record`() {
        val row = policy.stamp(emitted(timeMs = 71_000L, hints = 0), refKey, FirstWinPolicy.Decision.Replay(67_000L))

        assertFalse(row.isRecord)
        assertNull(row.firstCompletedAtMs)
        assertEquals(71_000L, row.timeMs)
        assertEquals(67_000L, row.completedAtMs)
    }
}
