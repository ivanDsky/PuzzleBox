package com.ivandsky.puzzlebox.play

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.domain.FinishLeg
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeMatchResultOutbox
import com.ivandsky.puzzlebox.testing.replay.ReplayFixtures
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A won duel leg queues its replay with the leg's result and stores the same replay with the
 * completion (advisory only; the time and hints are the record's either way).
 */
class LegCompletionReplayTest {

    private val game = TestGames.alpha
    private val day = LocalDate.parse("2026-11-04")
    private val ref = TestGames.Ref(game, PuzzleKey.Daily(day, 7))
    private val code = ShareCode.parse("M8T3VQ")!!
    private val legIndex = 1
    private val me = Identity(uid = "me", displayName = "Me", photoUrl = null)

    private val outbox = FakeMatchResultOutbox()
    private val completions = FakeCompletionRepository()
    private val repository = LegCompletionRepository(
        inner = completions,
        code = code,
        legIndex = legIndex,
        finishLeg = FinishLeg(outbox, FakeIdentityService(me)) { Instant.EPOCH },
    )

    private fun solve() = CompletionRecord(ref, time = 83_456, hints = 2, stats = RecordStats(1, "{}"))

    private fun queuedRow() = runBlocking { outbox.observeAll().first() }.single()

    @Test
    fun aWonLegWithAReplayQueuesTheReplayTheCompletionStored() {
        val replay = ReplayFixtures.sealed(moves = 3)

        runBlocking { repository.onCompleted(solve(), replay) }

        val row = queuedRow()
        assertEquals(replay, row.result.replay)
        assertEquals(replay, runBlocking { completions.latestReplay(ref) })
        assertEquals(83_456L, row.result.timeMs)
        assertEquals(2, row.result.hints)
        assertEquals(legIndex, row.result.legIndex)
    }

    @Test
    fun aWonLegWithANullReplayQueuesARowWithNone() {
        runBlocking { repository.onCompleted(solve(), null) }

        val row = queuedRow()
        assertNull(row.result.replay)
        assertNull(runBlocking { completions.latestReplay(ref) })
        assertEquals(83_456L, row.result.timeMs)
    }
}
