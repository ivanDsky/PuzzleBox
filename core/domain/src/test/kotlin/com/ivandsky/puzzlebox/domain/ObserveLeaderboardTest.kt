package com.ivandsky.puzzlebox.domain

import app.cash.turbine.test
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardSnapshot
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** One game's board for one day behind the identity gate (A7/A15), whichever game is featured. */
class ObserveLeaderboardTest {

    private val tango = GameId("tango")
    private val day = LocalDate.of(2026, 10, 30)
    private val boardRef = LeaderboardRef("tango", day)
    private val ann = Identity("u1", "Ann", null)

    @Test
    fun `signed in loads the given game's board for the given day`() = runTest {
        val entries = listOf(LeaderboardEntry("u2", "Bo", "tango/2026-10-30/12", 80_000, 0, 1_700_000_000_000))
        val leaderboard = FakeLeaderboardRepository(mapOf(boardRef to entries))

        ObserveLeaderboard(FakeIdentityService(initial = ann), leaderboard)(tango, day).test {
            assertEquals(TodayLeaderboard.SignedIn(ann, TodayLeaderboard.Board.Loading), awaitItem())
            assertEquals(
                TodayLeaderboard.SignedIn(ann, TodayLeaderboard.Board.Loaded(LeaderboardSnapshot(boardRef, entries))),
                awaitItem(),
            )
        }
        assertEquals(listOf(boardRef), leaderboard.observedBoards)
    }

    @Test
    fun `signed out observes no board`() = runTest {
        val leaderboard = FakeLeaderboardRepository()

        ObserveLeaderboard(FakeIdentityService(initial = null), leaderboard)(tango, day).test {
            assertEquals(TodayLeaderboard.SignedOut, awaitItem())
        }
        assertTrue(leaderboard.observedBoards.isEmpty())
    }

    @Test
    fun `an identity change restarts the board`() = runTest {
        val identity = FakeIdentityService(initial = ann)
        val leaderboard = FakeLeaderboardRepository()

        ObserveLeaderboard(identity, leaderboard)(tango, day).test {
            assertEquals(TodayLeaderboard.SignedIn(ann, TodayLeaderboard.Board.Loading), awaitItem())
            assertEquals(TodayLeaderboard.SignedIn(ann, TodayLeaderboard.Board.Unavailable), awaitItem())

            val bo = Identity("u2", "Bo", null)
            identity.currentIdentity = bo
            assertEquals(TodayLeaderboard.SignedIn(bo, TodayLeaderboard.Board.Loading), awaitItem())
            assertEquals(TodayLeaderboard.SignedIn(bo, TodayLeaderboard.Board.Unavailable), awaitItem())
        }
        assertEquals(listOf(boardRef, boardRef), leaderboard.observedBoards)
    }
}
