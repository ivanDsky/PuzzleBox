package com.ivandsky.puzzlebox.data.match

import androidx.room.Room
import com.ivandsky.puzzlebox.database.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.database.SelfMatchEntity
import com.ivandsky.puzzlebox.database.SelfPastLegEntity
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.room.RoomQueryThread
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** What only the Room adapter has: the `self_matches` columns, rows it cannot read and writers racing on one row. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomSelfMatchRepositoryTest {

    private val db: PuzzleBoxDatabase = Room
        .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), PuzzleBoxDatabase::class.java)
        .allowMainThreadQueries()
        .setQueryExecutor(RoomQueryThread.executor)
        .build()
    private val dao = db.selfMatchDao()
    private val repository = RoomSelfMatchRepository(dao, FakeCompletionRepository(), TestGames.registry(listOf(TestGames.alpha)))

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `save writes the settings columns from the match`() = runTest {
        val legs = listOf(leg(TestGames.beta, 1), leg(TestGames.alpha, 2), leg(TestGames.beta, 3))

        repository.save(selfMatch(A, legs).copy(includeFriends = true), past(legs))

        val row = requireNotNull(dao.match(A.value))
        assertEquals(START.toEpochMilli(), row.createdAtMs)
        assertEquals(3, row.roundCount)
        assertEquals("beta,alpha", row.games)
        assertEquals(true, row.includeFriends)
        assertEquals(
            legs.indices.map { SelfPastLegEntity(A.value, it, 60_000L + it, hints = it, completedAtMs = 1_000L + it) },
            dao.pastLegs(A.value),
        )
    }

    @Test
    fun `post rewrites only the legs text and leaves past you alone`() = runTest {
        val legs = listOf(leg(TestGames.alpha, 1), leg(TestGames.alpha, 2))
        repository.save(selfMatch(A, legs), past(legs))
        val before = requireNotNull(dao.match(A.value))
        val pastBefore = dao.pastLegs(A.value)

        repository.post(A, mine(1))

        val after = requireNotNull(dao.match(A.value))
        assertEquals(before.copy(legs = after.legs), after)
        assertTrue(after.legs != before.legs)
        assertEquals(pastBefore, dao.pastLegs(A.value))
    }

    @Test
    fun `posts racing on one duel all land`() = runTest {
        val legs = (1..10).map { leg(TestGames.alpha, it) }
        repository.save(selfMatch(A, legs), past(legs))

        val outcomes = legs.indices.map { async(Dispatchers.Default) { repository.post(A, mine(it)) } }.awaitAll()

        assertEquals(List(10) { PostResult.Posted }, outcomes)
        val read = (repository.observe(A).first() as MatchFetch.Found).match
        assertEquals((0..9).toSet(), read.progress[SelfSeats.ME])
    }

    @Test
    fun `a row this version cannot read is held but not found and stays out of the list`() = runTest {
        val good = selfMatch(B, listOf(leg(TestGames.alpha, 1)))
        repository.save(good, past(good.legs))
        val unreadable = listOf(
            "",
            "2|You|~",
            "1|You",
            "1|~|~",
            "1|%zz|~",
            "1|You|~\n~|~|alpha/not-a-day/1",
            "1|You|~\n~|~",
            "1|You|~\n~|50000,2|alpha/2026-10-01/1",
            "1|You|~\n~|50000,2,9000,maybe|alpha/2026-10-01/1",
            "1|You|~\n%zz|~|alpha/2026-10-01/1",
        )

        unreadable.forEach { text ->
            dao.insertMatch(SelfMatchEntity(A.value, START.toEpochMilli() + 1, roundCount = 1, games = "alpha", includeFriends = false, legs = text))

            assertTrue("held: $text", repository.holds(A))
            assertEquals("observe: $text", MatchFetch.NotFound, repository.observe(A).first())
            assertEquals("list: $text", listOf(good), repository.observeAll().first())
            assertTrue("post: $text", repository.post(A, mine(0)) is PostResult.Rejected)
            assertEquals("read: $text", ResultRead.Missing, repository.readResult(A, 0, SelfSeats.PAST))
        }
    }

    @Test
    fun `a row whose code no longer parses stays out of the list`() = runTest {
        repository.save(selfMatch(A, listOf(leg(TestGames.alpha, 1))), emptyList())
        val row = requireNotNull(dao.match(A.value))

        dao.insertMatch(row.copy(code = "not a code!", createdAtMs = row.createdAtMs + 1))

        assertEquals(listOf(A), repository.observeAll().first().map { it.code })
    }

    private fun leg(game: GameId, number: Int) =
        MatchLeg(LegRef.Daily(game, LocalDate.of(2026, 10, 1), number), authorUid = null)

    private fun selfMatch(code: ShareCode, legs: List<MatchLeg>) = Match(
        code = code,
        formatVersion = Match.FORMAT_VERSION,
        createdAt = START,
        deadlineHours = SelfSeats.NO_DEADLINE_HOURS,
        challenger = Player(SelfSeats.ME, "You", null),
        invitedUid = null,
        opponent = Player(SelfSeats.PAST, SelfSeats.PAST_NAME, null),
        legs = legs,
        includeFriends = false,
        cancelled = false,
        declined = false,
        rematchOf = null,
        progress = mapOf(SelfSeats.PAST to legs.indices.toSet(), SelfSeats.ME to emptySet()),
    )

    private fun past(legs: List<MatchLeg>) = legs.indices.map { leg ->
        LegResult(SelfSeats.PAST, leg, timeMs = 60_000L + leg, hints = leg, completedAtMs = 1_000L + leg, gaveUp = false)
    }

    private fun mine(leg: Int) = LegResult(SelfSeats.ME, leg, timeMs = 50_000L, hints = 2, completedAtMs = 9_000L, gaveUp = false)

    private companion object {
        val A = requireNotNull(ShareCode.parse("ABC234"))
        val B = requireNotNull(ShareCode.parse("XYZ567"))
        val START: Instant = Instant.ofEpochMilli(1_700_000_000_000L)
    }
}
