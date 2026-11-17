package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.SelfSeats
import com.ivandsky.puzzlebox.data.match.isSelf
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pure builder: the self seats, no deadline, and exactly one frozen past result per leg. */
class BuildSelfMatchTest {

    private val code = ShareCode.parse("K7Q2MX")!!
    private val created = Instant.ofEpochMilli(5_000_000)

    private fun leg(day: Int) = MatchLeg(LegRef.Daily(alpha, LocalDate.of(2026, 11, day), day), authorUid = null)

    private fun record(day: Int, time: Long, hints: Int, at: Instant?) = CompletionRecord(
        puzzleRef = TestGames.Ref(alpha, PuzzleKey.Daily(LocalDate.of(2026, 11, day), day)),
        time = time,
        hints = hints,
        stats = RecordStats(schemaVersion = 1, blob = "{}"),
        firstCompletedAt = at,
        isRecord = true,
    )

    private val legs = listOf(leg(1), leg(2), leg(3))
    private val records = listOf(
        record(1, 41_000, 0, Instant.ofEpochMilli(1_000)),
        record(2, 52_000, 2, Instant.ofEpochMilli(2_000)),
        record(3, 63_000, 1, Instant.ofEpochMilli(3_000)),
    )

    @Test
    fun `emits exactly one past result per leg from the record`() {
        val draft = buildSelfMatch(code, created, includeFriends = false, legs = legs, records = records)

        assertEquals(listOf(0, 1, 2), draft.past.map { it.legIndex })
        assertEquals(listOf(41_000L, 52_000L, 63_000L), draft.past.map { it.timeMs })
        assertEquals(listOf(0, 2, 1), draft.past.map { it.hints })
        assertEquals(listOf(1_000L, 2_000L, 3_000L), draft.past.map { it.completedAtMs })
        assertTrue(draft.past.all { it.uid == SelfSeats.PAST && !it.gaveUp })
    }

    @Test
    fun `builds a self match with both seats, no invite and no deadline`() {
        val match = buildSelfMatch(code, created, includeFriends = true, legs = legs, records = records).match

        assertTrue(match.isSelf)
        assertEquals(SelfSeats.PAST, match.opponent?.uid)
        assertEquals(SelfSeats.PAST_NAME, match.opponent?.name)
        assertNull(match.invitedUid)
        assertEquals(SelfSeats.NO_DEADLINE_HOURS, match.deadlineHours)
        assertEquals(legs, match.legs)
        assertEquals(setOf(0, 1, 2), match.progress[SelfSeats.PAST])
        assertTrue(match.progress[SelfSeats.ME].orEmpty().isEmpty())
        assertTrue(match.includeFriends)
        assertFalse(match.cancelled || match.declined)
        assertNull(match.rematchOf)
    }
}
