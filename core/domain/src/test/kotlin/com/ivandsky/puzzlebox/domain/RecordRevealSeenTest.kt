package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.match.LocalRole
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.repository.FakeMatchLocalRepository
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The reveal's seen marker and scores, stamped by the injected clock; a second opening keeps the first of both. */
class RecordRevealSeenTest {

    private val code = ShareCode.parse("K7Q2MX")!!
    private val local = FakeMatchLocalRepository()

    @Test
    fun theSeenMarkerAndScoresAreWrittenOnceWithTheInjectedClock() = runTest {
        local.remember(code, LocalRole.Participant, "Bob")
        val first = Instant.parse("2026-11-09T10:00:00Z")
        var now = first
        val recordSeen = RecordRevealSeen(local) { now }

        recordSeen(code, 2, 1)
        now = first.plusSeconds(90)
        recordSeen(code, 0, 3)

        val seen = local.observeAll().first().single()
        assertEquals(first, seen.revealSeenAt)
        assertEquals(2, seen.myScore)
        assertEquals(1, seen.theirScore)
    }
}
