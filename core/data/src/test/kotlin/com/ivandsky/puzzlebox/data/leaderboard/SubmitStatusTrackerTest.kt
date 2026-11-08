package com.ivandsky.puzzlebox.data.leaderboard

import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The submit status per (uid, puzzleRef), read by the board the puzzle belongs to. */
class SubmitStatusTrackerTest {

    private val day = LocalDate.of(2026, 10, 31)
    private val board = LeaderboardRef("zip", day)

    @Test
    fun `an untried post reads Posting`() = runTest {
        assertEquals(SubmitStatus.Posting, SubmitStatusTracker().observe("u1", board).first())
    }

    @Test
    fun `the latest status for the board's puzzle wins, per player`() = runTest {
        val tracker = SubmitStatusTracker()
        tracker.set("u1", "zip/2026-10-31/592", SubmitStatus.Deferred)
        tracker.set("u1", "zip/2026-10-31/592", SubmitStatus.Posted)
        tracker.set("u2", "zip/2026-10-31/592", SubmitStatus.Rejected)
        tracker.set("u1", "zip/2026-10-30/591", SubmitStatus.Rejected)

        assertEquals(SubmitStatus.Posted, tracker.observe("u1", board).first())
        assertEquals(SubmitStatus.Rejected, tracker.observe("u2", board).first())
        assertEquals(SubmitStatus.Posting, tracker.observe("u1", LeaderboardRef("tango", day)).first())
    }
}
