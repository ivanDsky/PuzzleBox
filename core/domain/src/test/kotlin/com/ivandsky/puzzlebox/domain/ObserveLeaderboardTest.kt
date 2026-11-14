package com.ivandsky.puzzlebox.domain

import app.cash.turbine.test
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRepository
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardSnapshot
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.game.ScoreModel
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.flowOf
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
    private val registry = TestGames.registry(listOf(tango))
    private val calendar = FakeGameCalendar(day)

    private fun entry(uid: String, timeMs: Long, hints: Int) =
        LeaderboardEntry(uid, uid, "tango/2026-10-30/12", timeMs, hints, 1_700_000_000_000)

    private fun hintsFirst(plugin: GamePlugin<Unit, TestGames.Session, TestGames.Model>) = GamePlugin(
        definition = plugin.definition,
        rules = plugin.rules,
        session = plugin.session,
        score = object : ScoreModel<TestGames.Session> {
            override fun completion(session: TestGames.Session) = plugin.score.completion(session)
            override val rankOrder = RankOrder.HintsThenTime
        },
        stateOf = plugin.stateOf,
        initialSession = plugin.initialSession,
        refOf = plugin.refOf,
        playAgainSession = plugin.playAgainSession,
        rendererModel = plugin.rendererModel,
    )

    private suspend fun boardOrder(plugin: GamePlugin<Unit, TestGames.Session, TestGames.Model>): List<String> {
        val entries = listOf(entry("slow", 200_000, 0), entry("fast", 50_000, 3), entry("quick", 100_000, 0))
        val leaderboard = FakeLeaderboardRepository(mapOf(boardRef to entries))
        var uids = emptyList<String>()
        ObserveLeaderboard(FakeIdentityService(initial = ann), leaderboard, GameRegistry(listOf(plugin)), calendar)(tango, day).test {
            awaitItem()
            val loaded = awaitItem() as TodayLeaderboard.SignedIn
            uids = (loaded.board as TodayLeaderboard.Board.Loaded).snapshot.entries.map { it.uid }
        }
        return uids
    }

    @Test
    fun `a hints-first game's board arrives fewest hints first, ties by time`() = runTest {
        assertEquals(
            listOf("quick", "slow", "fast"),
            boardOrder(hintsFirst(TestGames.plugin(tango))),
        )
    }

    @Test
    fun `a time-first game's board arrives fastest first, ties by hints`() = runTest {
        assertEquals(
            listOf("fast", "quick", "slow"),
            boardOrder(TestGames.plugin(tango)),
        )
    }

    @Test
    fun `signed in loads the given game's board for the given day`() = runTest {
        val entries = listOf(LeaderboardEntry("u2", "Bo", "tango/2026-10-30/12", 80_000, 0, 1_700_000_000_000))
        val leaderboard = FakeLeaderboardRepository(mapOf(boardRef to entries))

        ObserveLeaderboard(FakeIdentityService(initial = ann), leaderboard, registry, calendar)(tango, day).test {
            assertEquals(TodayLeaderboard.SignedIn(ann, TodayLeaderboard.Board.Loading), awaitItem())
            assertEquals(
                TodayLeaderboard.SignedIn(ann, TodayLeaderboard.Board.Loaded(LeaderboardSnapshot(boardRef, entries))),
                awaitItem(),
            )
        }
        assertEquals(listOf(boardRef), leaderboard.observedBoards)
    }

    @Test
    fun `an empty answer from the cache is still a loaded empty board`() = runTest {
        val cached = LeaderboardSnapshot(boardRef, emptyList(), fromCache = true)
        val leaderboard = object : LeaderboardRepository by FakeLeaderboardRepository() {
            override fun observe(board: LeaderboardRef, order: RankOrder) = flowOf<LeaderboardSnapshot?>(cached)
        }

        ObserveLeaderboard(FakeIdentityService(initial = ann), leaderboard, registry, calendar)(tango, day).test {
            awaitItem()
            assertEquals(TodayLeaderboard.SignedIn(ann, TodayLeaderboard.Board.Loaded(cached)), awaitItem())
        }
    }

    @Test
    fun `signed out observes no board`() = runTest {
        val leaderboard = FakeLeaderboardRepository()

        ObserveLeaderboard(FakeIdentityService(initial = null), leaderboard, registry, calendar)(tango, day).test {
            assertEquals(TodayLeaderboard.SignedOut, awaitItem())
        }
        assertTrue(leaderboard.observedBoards.isEmpty())
    }

    @Test
    fun `an identity change restarts the board`() = runTest {
        val identity = FakeIdentityService(initial = ann)
        val leaderboard = FakeLeaderboardRepository()

        ObserveLeaderboard(identity, leaderboard, registry, calendar)(tango, day).test {
            assertEquals(TodayLeaderboard.SignedIn(ann, TodayLeaderboard.Board.Loading), awaitItem())
            assertEquals(TodayLeaderboard.SignedIn(ann, TodayLeaderboard.Board.Unavailable), awaitItem())

            val bo = Identity("u2", "Bo", null)
            identity.currentIdentity = bo
            assertEquals(TodayLeaderboard.SignedIn(bo, TodayLeaderboard.Board.Loading), awaitItem())
            assertEquals(TodayLeaderboard.SignedIn(bo, TodayLeaderboard.Board.Unavailable), awaitItem())
        }
        assertEquals(listOf(boardRef, boardRef), leaderboard.observedBoards)
    }

    @Test
    fun `today loads the game's board for its calendar day, not the device date`() = runTest {
        val entries = listOf(LeaderboardEntry("u2", "Bo", "tango/2026-10-30/12", 80_000, 0, 1_700_000_000_000))
        val leaderboard = FakeLeaderboardRepository(mapOf(boardRef to entries))

        ObserveLeaderboard(FakeIdentityService(initial = ann), leaderboard, registry, calendar).today(tango).test {
            assertEquals(TodayLeaderboard.SignedIn(ann, TodayLeaderboard.Board.Loading), awaitItem())
            assertEquals(
                TodayLeaderboard.SignedIn(ann, TodayLeaderboard.Board.Loaded(LeaderboardSnapshot(boardRef, entries))),
                awaitItem(),
            )
        }
        assertEquals(listOf(boardRef), leaderboard.observedBoards)
    }

    @Test
    fun `today switches boards when the game's calendar rolls over`() = runTest {
        val leaderboard = FakeLeaderboardRepository()
        val nextDay = day.plusDays(1)

        ObserveLeaderboard(FakeIdentityService(initial = ann), leaderboard, registry, calendar).today(tango).test {
            assertEquals(TodayLeaderboard.SignedIn(ann, TodayLeaderboard.Board.Loading), awaitItem())
            assertEquals(TodayLeaderboard.SignedIn(ann, TodayLeaderboard.Board.Unavailable), awaitItem())

            calendar.set(tango, nextDay)
            assertEquals(TodayLeaderboard.SignedIn(ann, TodayLeaderboard.Board.Loading), awaitItem())
            assertEquals(TodayLeaderboard.SignedIn(ann, TodayLeaderboard.Board.Unavailable), awaitItem())
        }
        assertEquals(listOf(boardRef, LeaderboardRef("tango", nextDay)), leaderboard.observedBoards)
    }

    @Test
    fun `today ignores another game's rollover and signed out observes nothing`() = runTest {
        val leaderboard = FakeLeaderboardRepository()
        val signedOut = ObserveLeaderboard(FakeIdentityService(initial = null), leaderboard, registry, calendar)

        signedOut.today(tango).test {
            assertEquals(TodayLeaderboard.SignedOut, awaitItem())
            calendar.set(GameId("zip"), day.plusDays(1))
            expectNoEvents()
        }
        assertTrue(leaderboard.observedBoards.isEmpty())
    }
}
