package com.ivandsky.puzzlebox.domain

import app.cash.turbine.test
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The Results rank row derived from today's board: position, count, top percent, or hidden. */
class ObserveResultRankTest {

    private val tango = GameId("tango")
    private val day = LocalDate.of(2026, 11, 7)
    private val board = LeaderboardRef("tango", day)
    private val me = Identity("me", "Me", null)

    private fun entry(uid: String, seconds: Int) =
        LeaderboardEntry(uid, uid, "tango/2026-11-07/12", seconds * 1_000L, 0, 1_700_000_000_000)

    private fun rank(identity: Identity?, repo: FakeLeaderboardRepository) =
        ObserveResultRank(ObserveLeaderboard(FakeIdentityService(initial = identity), repo, TestGames.registry(listOf(tango))))(tango, day)

    @Test
    fun `a ranked player gets position count and top percent`() = runTest {
        // 4 players, me third: ceil(3 / 4 * 100) = 75.
        val repo = FakeLeaderboardRepository(
            mapOf(board to listOf(entry("a", 30), entry("b", 40), entry("me", 50), entry("c", 60))),
        )
        rank(me, repo).test {
            assertEquals(ResultRank.Hidden, awaitItem())
            assertEquals(ResultRank.Ranked(position = 3, count = 4, topPercent = 75), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `top percent never drops below one`() = runTest {
        val others = (1..500).map { entry("p$it", 100 + it) }
        val repo = FakeLeaderboardRepository(mapOf(board to listOf(entry("me", 10)) + others))
        rank(me, repo).test {
            awaitItem()
            assertEquals(ResultRank.Ranked(position = 1, count = 501, topPercent = 1), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `loading and unavailable boards hide the row`() = runTest {
        rank(me, FakeLeaderboardRepository()).test {
            assertEquals(ResultRank.Hidden, awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun `an own entry not posted yet hides the row until it lands`() = runTest {
        val repo = FakeLeaderboardRepository(mapOf(board to listOf(entry("a", 30))))
        rank(me, repo).test {
            assertEquals(ResultRank.Hidden, awaitItem())
            expectNoEvents()
            repo.setEntries(board, listOf(entry("a", 30), entry("me", 45)))
            assertEquals(ResultRank.Ranked(position = 2, count = 2, topPercent = 100), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `signed out offers sign in`() = runTest {
        rank(null, FakeLeaderboardRepository()).test {
            assertEquals(ResultRank.SignedOut, awaitItem())
            expectNoEvents()
        }
    }
}
