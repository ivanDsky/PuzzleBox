package com.ivandsky.puzzlebox.domain

import app.cash.turbine.test
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRepository
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardSnapshot
import com.ivandsky.puzzlebox.data.leaderboard.SubmitResult
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Identity-gated board (ticket 16): signed out observes nothing; identity changes restart the board. */
class ObserveTodayLeaderboardTest {

    private val day = LocalDate.of(2026, 10, 31)
    private val boardRef = LeaderboardRef("zip", day)
    private val ann = Identity("u1", "Ann", null)

    private val registry = TestGames.registry(listOf(GameId("zip"), GameId("tango")))

    private fun observe(identity: FakeIdentityService, leaderboard: LeaderboardRepository) =
        ObserveTodayLeaderboard(
            leaderboard = ObserveLeaderboard(identity, leaderboard, registry),
            featured = GetFeaturedGame(registry),
            today = { day },
        )()

    @Test
    fun `signed out observes no board (A7-A15)`() = runTest {
        val leaderboard = FakeLeaderboardRepository()
        observe(FakeIdentityService(initial = null), leaderboard).test {
            assertEquals(TodayLeaderboard.SignedOut, awaitItem())
        }
        assertTrue(leaderboard.observedBoards.isEmpty())
    }

    @Test
    fun `signed in loads the featured game's board for today`() = runTest {
        val leaderboard = FakeLeaderboardRepository()
        val entries = listOf(
            LeaderboardEntry("u2", "Bo", "zip/2026-10-31/592", 80_000, 0, 1_700_000_000_000),
        )
        leaderboard.setEntries(boardRef, entries)

        observe(FakeIdentityService(initial = ann), leaderboard).test {
            assertEquals(TodayLeaderboard.SignedIn(ann, TodayLeaderboard.Board.Loading), awaitItem())
            assertEquals(
                TodayLeaderboard.SignedIn(ann, TodayLeaderboard.Board.Loaded(LeaderboardSnapshot(boardRef, entries))),
                awaitItem(),
            )
        }
        assertEquals(listOf(boardRef), leaderboard.observedBoards)
    }

    @Test
    fun `a null snapshot is the soft unavailable state (A7)`() = runTest {
        val unavailable = object : LeaderboardRepository {
            override fun observe(board: LeaderboardRef, order: RankOrder): Flow<LeaderboardSnapshot?> = flowOf(null)
            override suspend fun submit(entry: LeaderboardEntry): SubmitResult = error("unused")
        }

        observe(FakeIdentityService(initial = ann), unavailable).test {
            assertEquals(TodayLeaderboard.SignedIn(ann, TodayLeaderboard.Board.Loading), awaitItem())
            assertEquals(TodayLeaderboard.SignedIn(ann, TodayLeaderboard.Board.Unavailable), awaitItem())
            // The identity stream stays open; the soft state is the board's last word.
            expectNoEvents()
        }
    }

    @Test
    fun `an identity change restarts the board, sign-out drops it`() = runTest {
        val identity = FakeIdentityService(initial = ann)
        val leaderboard = FakeLeaderboardRepository()

        observe(identity, leaderboard).test {
            assertEquals(TodayLeaderboard.SignedIn(ann, TodayLeaderboard.Board.Loading), awaitItem())
            assertEquals(TodayLeaderboard.SignedIn(ann, TodayLeaderboard.Board.Unavailable), awaitItem())

            val bo = Identity("u2", "Bo", null)
            identity.currentIdentity = bo
            assertEquals(TodayLeaderboard.SignedIn(bo, TodayLeaderboard.Board.Loading), awaitItem())
            assertEquals(TodayLeaderboard.SignedIn(bo, TodayLeaderboard.Board.Unavailable), awaitItem())

            identity.signOut()
            assertEquals(TodayLeaderboard.SignedOut, awaitItem())
        }
        assertEquals(listOf(boardRef, boardRef), leaderboard.observedBoards)
    }
}
