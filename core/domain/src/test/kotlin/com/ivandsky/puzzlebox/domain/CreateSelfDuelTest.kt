package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.LocalRole
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.data.match.MatchFetch
import com.ivandsky.puzzlebox.data.match.ResultRead
import com.ivandsky.puzzlebox.data.match.SelfSeats
import com.ivandsky.puzzlebox.domain.lineup.AnyGame
import com.ivandsky.puzzlebox.domain.lineup.ExactPuzzle
import com.ivandsky.puzzlebox.domain.lineup.PuzzleSource
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchLocalRepository
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSelfMatchRepository
import java.time.Instant
import java.time.LocalDate
import kotlin.random.Random
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Creating a self duel: draws only pool puzzles, freezes records, saves on the device, needs no identity. */
class CreateSelfDuelTest {

    private val registry = TestGames.registry(listOf(alpha, beta))
    private val completions = FakeCompletionRepository()
    private val received = FakeReceivedPuzzleRepository()
    private val self = FakeSelfMatchRepository()
    private val local = FakeMatchLocalRepository()
    private val now = Instant.ofEpochMilli(9_000_000)
    private val pool = ComputeSolvedPool(registry, completions, received)

    private fun create(seed: Int = 0) =
        CreateSelfDuel(pool, completions, received, self, local, { now }, Random(seed))

    private fun settings(rounds: Int = 3, games: Set<GameId> = setOf(alpha, beta), friends: Boolean = false) =
        SelfDuelSettings(rounds, games, friends)

    private fun daily(day: Int) = PuzzleKey.Daily(LocalDate.of(2026, 11, day), puzzleNumber = day)

    private fun record(game: GameId, key: PuzzleKey, time: Long = 30_000L, hints: Int = 0, at: Long = 1_000L, isRecord: Boolean = true) =
        CompletionRecord(
            puzzleRef = TestGames.Ref(game, key),
            time = time,
            hints = hints,
            stats = RecordStats(schemaVersion = 1, blob = "{}"),
            firstCompletedAt = Instant.ofEpochMilli(at),
            isRecord = isRecord,
        )

    private suspend fun friend(code: ShareCode) = received.upsert(
        ReceivedPuzzle(
            code = code, gameId = alpha, variant = TestGames.variant(alpha), sharedFormatVersion = 1,
            puzzleBytes = byteArrayOf(TestGames.MAGIC), title = "t", authorName = "bo", note = "",
            authorTimeMs = 1_000, authorHints = 0, authorUid = "bo", openedAt = Instant.EPOCH,
            unpublished = false, postState = PostState.Pending, postAttempts = 0, difficulty = null,
        ),
    )

    private fun created(outcome: CreateOutcome) = outcome as CreateOutcome.Created

    @Test
    fun `an empty pool answers NoPuzzles and saves nothing`() = runTest {
        assertEquals(CreateOutcome.NoPuzzles, create()(settings()))
        assertTrue(self.observeAll().first().isEmpty())
    }

    @Test
    fun `draws rounds legs from the pool and saves the duel with no identity at all`() = runTest {
        completions.setRecords((1..6).map { record(alpha, daily(it), time = it * 1_000L) })

        val outcome = created(create()(settings(rounds = 3)))

        assertEquals(3, outcome.legs.size)
        assertEquals(outcome.legs.toSet().size, outcome.legs.size)
        val saved = self.observeAll().first().single()
        assertEquals(outcome.code, saved.code)
        assertEquals(outcome.legs, saved.legs)
        assertEquals(now, saved.createdAt)
    }

    @Test
    fun `a pool smaller than rounds draws all of it, which is the pool size, friends included`() = runTest {
        val code = ShareCode.parse("K7Q2MX")!!
        friend(code)
        completions.setRecords(
            listOf(record(alpha, daily(1)), record(beta, daily(2)), record(alpha, PuzzleKey.Custom(code.value))),
        )
        for (friends in listOf(false, true)) {
            val size = pool(setOf(alpha, beta), friends).size
            val outcome = created(create()(settings(rounds = 10, friends = friends)))
            assertEquals(size, outcome.legs.size)
        }
    }

    @Test
    fun `draws only puzzles from the pool and friends only when on`() = runTest {
        val code = ShareCode.parse("K7Q2MX")!!
        friend(code)
        completions.setRecords(
            listOf(record(alpha, daily(1)), record(beta, daily(2)), record(alpha, PuzzleKey.Custom(code.value))),
        )

        val off = created(create()(settings(rounds = 10, games = setOf(alpha), friends = false)))
        assertEquals(1, off.legs.size)
        assertTrue(off.legs.single().ref is LegRef.Daily)

        val on = created(create()(settings(rounds = 10, games = setOf(alpha), friends = true)))
        assertEquals(2, on.legs.size)
        assertEquals("bo", on.legs.map { it.ref }.zip(on.legs).first { it.first is LegRef.Custom }.second.authorUid)
    }

    @Test
    fun `past you is the first win, never a later faster play`() = runTest {
        completions.setRecords(
            listOf(
                record(alpha, daily(1), time = 77_000, hints = 3, at = 4_000),
                record(alpha, daily(1), time = 10_000, hints = 0, at = 8_000, isRecord = false),
            ),
        )

        val code = created(create()(settings(rounds = 1))).code
        val past = (self.readResult(code, 0, SelfSeats.PAST) as ResultRead.Found).result

        assertEquals(77_000L, past.timeMs)
        assertEquals(3, past.hints)
        assertEquals(4_000L, past.completedAtMs)
    }

    @Test
    fun `one past result per leg is stored`() = runTest {
        completions.setRecords((1..3).map { record(alpha, daily(it)) })
        val code = created(create()(settings(rounds = 3))).code

        (0..2).forEach { assertTrue(self.readResult(code, it, SelfSeats.PAST) is ResultRead.Found) }
        assertEquals(ResultRead.Missing, self.readResult(code, 3, SelfSeats.PAST))
    }

    @Test
    fun `a code a friend duel already holds is never reused`() = runTest {
        completions.setRecords(listOf(record(alpha, daily(1))))
        // The code the seeded run would pick first: one puzzle is shuffled, then the code drawn.
        val first = Random(7).let { listOf(1).shuffled(it); ShareCode.random(it) }
        local.remember(first, LocalRole.Participant, opponentName = null)

        val code = created(create(seed = 7)(settings(rounds = 1))).code

        assertTrue(code != first)
        assertTrue(self.observe(code).first() is MatchFetch.Found)
    }

    @Test
    fun `a code a self duel already holds is never reused`() = runTest {
        completions.setRecords(listOf(record(alpha, daily(1))))
        val a = created(create(seed = 3)(settings(rounds = 1))).code
        val b = created(create(seed = 3)(settings(rounds = 1))).code

        assertTrue(a != b)
        assertEquals(2, self.observeAll().first().size)
    }

    @Test
    fun `a lineup saves a duel with its legs in order, two of one game different`() = runTest {
        completions.setRecords(listOf(record(alpha, daily(1)), record(alpha, daily(2)), record(beta, daily(3))))
        val lineup = listOf(AnyGame(alpha), AnyGame(beta), AnyGame(alpha))

        val outcome = created(create()(settings().copy(lineup = lineup)))

        assertEquals(listOf(alpha, beta, alpha), outcome.legs.map { it.ref.gameId })
        assertEquals(2, outcome.legs.map { it.ref }.filter { it.gameId == alpha }.toSet().size)
        assertEquals(outcome.legs, self.observeAll().first().single().legs)
    }

    @Test
    fun `a lineup leg's past result is the first solve, never a later faster play`() = runTest {
        completions.setRecords(
            listOf(
                record(alpha, daily(1), time = 77_000, hints = 3, at = 4_000),
                record(alpha, daily(1), time = 10_000, at = 8_000, isRecord = false),
            ),
        )

        val code = created(create()(settings().copy(lineup = listOf(AnyGame(alpha))))).code

        val past = (self.readResult(code, 0, SelfSeats.PAST) as ResultRead.Found).result
        assertEquals(77_000L, past.timeMs)
        assertEquals(3, past.hints)
    }

    @Test
    fun `a lineup row that can't be filled is refused at its position and saves nothing`() = runTest {
        completions.setRecords(listOf(record(alpha, daily(1)), record(beta, daily(3))))
        val lineup = listOf(AnyGame(alpha), AnyGame(beta), AnyGame(alpha))

        assertEquals(CreateOutcome.EntryUndrawable(3, alpha), create()(settings().copy(lineup = lineup)))
        val gone = ExactPuzzle(LegRef.Daily(alpha, LocalDate.of(2026, 11, 9), 9), null, "t", null, PuzzleSource.PastDay)
        assertEquals(CreateOutcome.EntryUnavailable(2), create()(settings().copy(lineup = listOf(AnyGame(alpha), gone))))
        assertTrue(self.observeAll().first().isEmpty())
    }

    @Test
    fun `an empty lineup answers NoPuzzles`() = runTest {
        completions.setRecords(listOf(record(alpha, daily(1))))
        assertEquals(CreateOutcome.NoPuzzles, create()(settings().copy(lineup = emptyList())))
    }
}
