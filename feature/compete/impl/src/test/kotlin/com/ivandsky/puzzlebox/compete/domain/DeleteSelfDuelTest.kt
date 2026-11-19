package com.ivandsky.puzzlebox.compete.domain

import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.LegResult
import com.ivandsky.puzzlebox.data.match.LocalRole
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.SelfSeats
import com.ivandsky.puzzlebox.domain.buildSelfMatch
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchLocalRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchResultOutbox
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSelfMatchRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Removing an unfinished self duel clears every trace of it and nothing else. */
class DeleteSelfDuelTest {

    private val self = FakeSelfMatchRepository()
    private val outbox = FakeMatchResultOutbox()
    private val plays = FakeSavedPlayRepository()
    private val local = FakeMatchLocalRepository()
    private val completions = FakeCompletionRepository()
    private val delete = DeleteSelfDuel(self, outbox, plays, local)

    private val day = LocalDate.of(2026, 11, 3)
    private val legs = listOf(
        MatchLeg(LegRef.Daily(alpha, day, 1), authorUid = null),
        MatchLeg(LegRef.Daily(beta, day, 2), authorUid = null),
    )
    private val mine = ShareCode.parse("ABC234")!!
    private val friendCode = ShareCode.parse("XYZ567")!!

    private fun record(leg: MatchLeg) = CompletionRecord(
        puzzleRef = TestGames.Ref(leg.ref.gameId, PuzzleKey.Daily(day, puzzleNumber = 1)),
        time = 30_000L,
        hints = 0,
        stats = RecordStats(schemaVersion = 1, blob = "{}"),
        firstCompletedAt = Instant.ofEpochMilli(1_000),
        isRecord = true,
    )

    private suspend fun seedSelfDuel() {
        val draft = buildSelfMatch(mine, Instant.ofEpochMilli(5_000), false, legs, legs.map(::record))
        self.save(draft.match, draft.past)
        outbox.enqueue(mine, LegResult(SelfSeats.ME, 0, 20_000, 0, 6_000, false))
        local.remember(mine, LocalRole.Participant, SelfSeats.PAST_NAME)
        legs.forEachIndexed { i, leg -> plays.save(PlaySlot.Leg(leg.ref.gameId, mine.value, i), byteArrayOf(1), 100) }
        completions.setRecords(legs.map(::record))
    }

    @Test
    fun `removes the self rows, outbox rows, leg plays and local row, and keeps the solves`() = runTest {
        seedSelfDuel()

        delete(mine)

        assertTrue(self.observeAll().first().isEmpty())
        assertTrue(outbox.observe(mine).first().isEmpty())
        assertTrue(plays.rows.isEmpty())
        assertTrue(local.observeAll().first().isEmpty())
        assertTrue(local.opponentNames().first().isEmpty())
        assertEquals(legs.map(::record), completions.observeRecords().first())
    }

    @Test
    fun `an unknown or friend code changes nothing`() = runTest {
        seedSelfDuel()
        outbox.enqueue(friendCode, LegResult("me-uid", 0, 20_000, 0, 6_000, false))
        local.remember(friendCode, LocalRole.Participant, "Ann")
        plays.save(PlaySlot.Leg(alpha, friendCode.value, 0), byteArrayOf(2), 100)

        delete(friendCode)

        assertEquals(1, self.observeAll().first().size)
        assertEquals(1, outbox.observe(friendCode).first().size)
        assertEquals(1, outbox.observe(mine).first().size)
        assertNotNull(local.observeAll().first().firstOrNull { it.code == friendCode })
        assertEquals(3, plays.rows.size)
        assertNull(plays.load(PlaySlot.Leg(alpha, "HJK789", 0)))
    }
}
