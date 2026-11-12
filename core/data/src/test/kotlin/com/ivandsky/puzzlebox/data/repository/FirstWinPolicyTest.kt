package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.model.PuzzleKey
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

/** Pure first-win decision: first win is the record; play-agains never become it. */
class FirstWinPolicyTest {

    private val policy = FirstWinPolicy()
    private val day = LocalDate.of(2026, 10, 31)
    private val refKey = "zip/2026-10-31/592"

    private fun emitted(timeMs: Long, hints: Int, matchCode: String? = null) = CompletionRecord(
        puzzleRef = Ref(GameId("zip"), PuzzleKey.Daily(day, 592)),
        time = timeMs,
        hints = hints,
        stats = RecordStats(schemaVersion = 1, blob = """{"v":1,"mistakes":3}"""),
        matchCode = matchCode,
    )

    @Test
    fun `first completion of a ref decides record`() {
        val decision = policy.decide(hasRecord = false, nowMs = 7_000L)
        assertEquals(FirstWinPolicy.Decision.Record(7_000L), decision)
    }

    @Test
    fun `later completion of the same ref decides play-again`() {
        val decision = policy.decide(hasRecord = true, nowMs = 67_000L)
        assertEquals(FirstWinPolicy.Decision.PlayAgain(67_000L), decision)
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
    fun `play-again stamp never claims the record`() {
        val row = policy.stamp(emitted(timeMs = 71_000L, hints = 0), refKey, FirstWinPolicy.Decision.PlayAgain(67_000L))

        assertFalse(row.isRecord)
        assertNull(row.firstCompletedAtMs)
        assertEquals(71_000L, row.timeMs)
        assertEquals(67_000L, row.completedAtMs)
    }

    @Test
    fun `record stamp carries the match code`() {
        val row = policy.stamp(emitted(95_000L, 0, matchCode = "ABC123"), refKey, FirstWinPolicy.Decision.Record(7_000L))
        assertEquals("ABC123", row.matchCode)
    }

    @Test
    fun `play-again stamp carries the match code`() {
        val row = policy.stamp(emitted(71_000L, 0, matchCode = "XYZ789"), refKey, FirstWinPolicy.Decision.PlayAgain(67_000L))
        assertEquals("XYZ789", row.matchCode)
    }

    @Test
    fun `a record without a match code stamps null`() {
        val row = policy.stamp(emitted(95_000L, 0), refKey, FirstWinPolicy.Decision.Record(7_000L))
        assertNull(row.matchCode)
    }
}
