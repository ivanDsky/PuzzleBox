package com.ivandsky.puzzlebox.domain

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.linkedin.LinkState
import com.ivandsky.puzzlebox.data.linkedin.LinkedInDayData
import com.ivandsky.puzzlebox.data.linkedin.LinkedInGhost
import com.ivandsky.puzzlebox.data.linkedin.LinkedInOwner
import com.ivandsky.puzzlebox.data.linkedin.LinkedInOwnState
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInBoardView
import com.ivandsky.puzzlebox.domain.linkedin.ObserveLinkedInDay
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.CompletionSource
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInDays
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.first
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

    private val registry = TestGames.registry(listOf(tango))
    private val days = FakeLinkedInDays()

    private fun rank(identity: Identity?, repo: FakeLeaderboardRepository, withConnections: Boolean = false) =
        ObserveResultRank(ObserveLeaderboard(FakeIdentityService(initial = identity), repo, registry, FakeGameCalendar(day)), registry)(
            tango,
            day,
            connections = if (withConnections) ObserveLinkedInDay(days, registry)(tango, day) else null,
        )

    private fun ghost(i: Int, seconds: Int?, hintFree: Boolean = true) =
        LinkedInGhost("member-$i", "Member $i", seconds?.let { it * 1_000L }, hintFree, flawless = false)

    private fun imported(seconds: Int, hints: Int = 0) = LinkedInOwnState.Imported(
        CompletionRecord(
            puzzleRef = TestGames.Ref(tango, PuzzleKey.Daily(day, 12)),
            time = seconds * 1_000L,
            hints = hints,
            stats = RecordStats(schemaVersion = 1, blob = "{}"),
            source = CompletionSource.LinkedIn,
        ),
    )

    /** A connected day whose connections board was read. */
    private fun connections(
        vararg ghosts: LinkedInGhost,
        own: LinkedInOwnState = LinkedInOwnState.Unknown,
        owner: LinkedInOwner? = null,
    ) = days.emit(
        tango,
        day,
        LinkedInDayData(
            link = LinkState.Connected("me"),
            delta = 0,
            boardInWindow = true,
            inFlight = false,
            lastSync = null,
            own = own,
            ghosts = ghosts.toList(),
            boardFetchedAtMs = 1L,
            owner = owner,
        ),
    )

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

    private val fourPlayers = FakeLeaderboardRepository(
        mapOf(board to listOf(entry("a", 30), entry("b", 40), entry("me", 50), entry("c", 60))),
    )

    @Test
    fun `connections ahead of the player push the combined place down and join the count`() = runTest {
        connections(ghost(1, 20), ghost(2, 45), ghost(3, 70))
        rank(me, fourPlayers, withConnections = true).test {
            assertEquals(
                ResultRank.Ranked(position = 3, count = 4, topPercent = 75, withConnections = ResultRank.WithConnections(position = 5, count = 7)),
                nextRanked(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a connection with the player's time shares the place and does not push it`() = runTest {
        connections(ghost(1, 50))
        rank(me, fourPlayers, withConnections = true).test {
            assertEquals(ResultRank.WithConnections(position = 3, count = 5), nextRanked().withConnections)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a hinted connection is behind a hint free player at the same time but ahead with a faster one`() = runTest {
        connections(ghost(1, 50, hintFree = false), ghost(2, 40, hintFree = false))
        rank(me, fourPlayers, withConnections = true).test {
            assertEquals(ResultRank.WithConnections(position = 4, count = 6), nextRanked().withConnections)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a hidden connection time takes no place and is not counted`() = runTest {
        connections(ghost(1, null), ghost(2, 10))
        rank(me, fourPlayers, withConnections = true).test {
            assertEquals(ResultRank.WithConnections(position = 4, count = 5), nextRanked().withConnections)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the day card and the result line place the player alike and count the same people`() = runTest {
        // The player and the two connections with a time are counted; the hidden time is not.
        connections(ghost(1, null), ghost(2, 20), ghost(3, 80), own = imported(50))
        val repo = FakeLeaderboardRepository(mapOf(board to listOf(entry("me", 50))))

        val card = (ObserveLinkedInDay(days, registry)(tango, day).first().board as LinkedInBoardView.Shown)
        rank(me, repo, withConnections = true).test {
            assertEquals(ResultRank.WithConnections(position = 2, count = 3), nextRanked().withConnections)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(2, card.yourRank)
        assertEquals(3, card.total)
    }

    @Test
    fun `an imported LinkedIn time replaces the player's own time in the combined place, not in the PuzzleBox one`() = runTest {
        // 35s moves the player from behind b (40s) to ahead of it, with no connection on the board at all.
        connections(own = imported(35))
        rank(me, fourPlayers, withConnections = true).test {
            assertEquals(
                ResultRank.Ranked(position = 3, count = 4, topPercent = 75, withConnections = ResultRank.WithConnections(position = 2, count = 4)),
                nextRanked(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the player's own row stays the you row at the imported time and a same-name other entry is left alone`() = runTest {
        // The owner's LinkedIn name is "Owner Name", which another entry (60s) carries: the player's own entry, not that one,
        // takes the 35s import, so it is ahead of b (40s) and behind the 20s connection.
        val repo = FakeLeaderboardRepository(
            mapOf(
                board to listOf(
                    entry("a", 30),
                    entry("b", 40),
                    entry("me", 50),
                    LeaderboardEntry("o", "Owner Name", "tango/2026-11-07/12", 60_000L, 0, 1_700_000_000_000),
                ),
            ),
        )
        connections(ghost(1, 20), own = imported(35), owner = LinkedInOwner("Owner Name", photoPath = "/p/owner.jpg"))
        rank(me, repo, withConnections = true).test {
            assertEquals(
                ResultRank.Ranked(position = 3, count = 4, topPercent = 75, withConnections = ResultRank.WithConnections(position = 3, count = 5)),
                nextRanked(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a connection named like a PuzzleBox player is that player with the LinkedIn time, counted once`() = runTest {
        // Entry "c" (60s) is the connection "C " (20s): one person at 20s, so it is ahead of the player, and no one joins.
        connections(LinkedInGhost("member-1", "C ", 20_000L, hintFree = true, flawless = false), ghost(2, 70))
        rank(me, fourPlayers, withConnections = true).test {
            assertEquals(ResultRank.WithConnections(position = 4, count = 5), nextRanked().withConnections)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a same-name connection with a hidden time changes nothing`() = runTest {
        connections(LinkedInGhost("member-1", "c", null, hintFree = true, flawless = false))
        rank(me, fourPlayers, withConnections = true).test {
            assertEquals(ResultRank.Ranked(position = 3, count = 4, topPercent = 75), nextRanked())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a day with no connections ranks as the board alone`() = runTest {
        rank(me, fourPlayers, withConnections = true).test {
            assertEquals(ResultRank.Ranked(position = 3, count = 4, topPercent = 75), nextRanked())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `without the connections flow the rank ignores any cached ones`() = runTest {
        connections(ghost(1, 20))
        rank(me, fourPlayers, withConnections = false).test {
            assertEquals(ResultRank.Ranked(position = 3, count = 4, topPercent = 75), nextRanked())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `connections landing later update the line`() = runTest {
        rank(me, fourPlayers, withConnections = true).test {
            assertEquals(ResultRank.Ranked(position = 3, count = 4, topPercent = 75), nextRanked())
            connections(ghost(1, 20))
            assertEquals(ResultRank.WithConnections(position = 4, count = 5), nextRanked().withConnections)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** The first ranked row: a board still loading reads Hidden before it lands. */
    private suspend fun ReceiveTurbine<ResultRank>.nextRanked(): ResultRank.Ranked {
        var item = awaitItem()
        while (item !is ResultRank.Ranked) item = awaitItem()
        return item
    }
}
