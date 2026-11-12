package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.match.LegResult
import com.ivandsky.puzzlebox.data.match.OutboxResult
import com.ivandsky.puzzlebox.data.match.ResultPostState
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.model.SolveReplay
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeMatchResultOutbox
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Finishing a duel leg queues exactly one result per (match, leg, player). */
class FinishLegTest {

    private val now = Instant.ofEpochMilli(1_700_000_000_123)
    private val code = ShareCode.parse("K7Q2MX")!!
    private val otherCode = ShareCode.parse("ABCDEF")!!
    private val outbox = FakeMatchResultOutbox()
    private val identity = FakeIdentityService(Identity("me", "Me", null))
    private val finishLeg = FinishLeg(outbox, identity) { now }
    private val replay = SolveReplay.of(byteArrayOf(1, 2, 3))!!

    private suspend fun rows(c: ShareCode = code): List<OutboxResult> = outbox.observe(c).first()

    @Test
    fun aSolveQueuesAPendingResultWithTheClockTime() = runTest {
        val answer = finishLeg(code, 1, LegFinish.Solved(timeMs = 83_456, hints = 2))

        assertEquals(FinishResult.Queued, answer)
        assertEquals(
            listOf(OutboxResult(code, LegResult("me", 1, 83_456, 2, now.toEpochMilli(), gaveUp = false), ResultPostState.Pending, 0)),
            rows(),
        )
    }

    @Test
    fun aSolveWithAReplayQueuesTheReplayWithTheResult() = runTest {
        val answer = finishLeg(code, 1, LegFinish.Solved(timeMs = 83_456, hints = 2, replay = replay))

        assertEquals(FinishResult.Queued, answer)
        assertEquals(
            listOf(LegResult("me", 1, 83_456, 2, now.toEpochMilli(), gaveUp = false, replay = replay)),
            rows().map { it.result },
        )
    }

    @Test
    fun aGiveUpQueuesALossWithZeroTimeAndNoHints() = runTest {
        val answer = finishLeg(code, 0, LegFinish.GaveUp())

        assertEquals(FinishResult.Queued, answer)
        assertEquals(
            listOf(LegResult("me", 0, 0, 0, now.toEpochMilli(), gaveUp = true)),
            rows().map { it.result },
        )
    }

    @Test
    fun aGiveUpWithAReplayQueuesTheReplayWithTheLoss() = runTest {
        val answer = finishLeg(code, 0, LegFinish.GaveUp(replay))

        assertEquals(FinishResult.Queued, answer)
        assertEquals(
            listOf(LegResult("me", 0, 0, 0, now.toEpochMilli(), gaveUp = true, replay = replay)),
            rows().map { it.result },
        )
    }

    @Test
    fun aSecondFinishForTheSameLegIsAlreadyRecordedAndKeepsTheFirst() = runTest {
        finishLeg(code, 0, LegFinish.GaveUp())
        val first = rows()

        val answer = finishLeg(code, 0, LegFinish.Solved(timeMs = 5_000, hints = 0))

        assertEquals(FinishResult.AlreadyRecorded, answer)
        assertEquals(first, rows())
        assertEquals(FinishResult.AlreadyRecorded, finishLeg(code, 0, LegFinish.GaveUp()))
    }

    @Test
    fun signedOutQueuesNothing() = runTest {
        identity.currentIdentity = null

        val answer = finishLeg(code, 0, LegFinish.Solved(timeMs = 5_000, hints = 0))

        assertEquals(FinishResult.SignedOut, answer)
        assertTrue(outbox.observeAll().first().isEmpty())
    }

    @Test
    fun legsOfOneMatchAndOfOtherMatchesAreIndependent() = runTest {
        assertEquals(FinishResult.Queued, finishLeg(code, 0, LegFinish.Solved(1_000, 0)))
        assertEquals(FinishResult.Queued, finishLeg(code, 1, LegFinish.Solved(2_000, 0)))
        assertEquals(FinishResult.Queued, finishLeg(otherCode, 0, LegFinish.Solved(3_000, 0)))

        assertEquals(listOf(0, 1), rows().map { it.result.legIndex })
        assertEquals(listOf(3_000L), rows(otherCode).map { it.result.timeMs })
    }
}
