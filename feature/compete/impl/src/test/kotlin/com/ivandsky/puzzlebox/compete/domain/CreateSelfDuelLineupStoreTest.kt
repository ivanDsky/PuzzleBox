package com.ivandsky.puzzlebox.compete.domain

import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.LegResult
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.data.match.MatchFetch
import com.ivandsky.puzzlebox.data.match.ResultPostState
import com.ivandsky.puzzlebox.data.match.ResultRead
import com.ivandsky.puzzlebox.data.match.SelfSeats
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.domain.ComputeMatchOutcome
import com.ivandsky.puzzlebox.domain.ComputeSolvedPool
import com.ivandsky.puzzlebox.domain.CreateOutcome
import com.ivandsky.puzzlebox.domain.LegBasis
import com.ivandsky.puzzlebox.domain.LegVerdict
import com.ivandsky.puzzlebox.domain.MatchVerdict
import com.ivandsky.puzzlebox.domain.lineup.AnyGame
import com.ivandsky.puzzlebox.domain.lineup.ExactPuzzle
import com.ivandsky.puzzlebox.domain.lineup.LineupEntry
import com.ivandsky.puzzlebox.domain.lineup.PuzzleSource
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.testing.game.TestGames.beta
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchLocalRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchResultOutbox
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSelfMatchRepository
import java.time.Instant
import java.time.LocalDate
import kotlin.random.Random
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A Past you duel built from a lineup is stored like the one the random path builds: the same `Match` apart from its
 * code and legs, one frozen first-solve result per leg in the lineup's order, and it reveals through the same rule.
 */
class CreateSelfDuelLineupStoreTest {

    private val registry = TestGames.registry(listOf(alpha, beta))
    private val completions = FakeCompletionRepository()
    private val received = FakeReceivedPuzzleRepository()
    private val self = FakeSelfMatchRepository()
    private val outbox = FakeMatchResultOutbox()
    private val now = Instant.ofEpochMilli(9_000_000)
    private val pool = ComputeSolvedPool(registry, completions, received)
    private val friendCode = ShareCode.parse("K7Q2MX")!!

    private fun create(seed: Int = 0) =
        CreateSelfDuel(pool, completions, received, self, FakeMatchLocalRepository(), { now }, Random(seed))

    private fun daily(day: Int) = PuzzleKey.Daily(LocalDate.of(2026, 11, day), puzzleNumber = day)

    private fun record(game: GameId, key: PuzzleKey, time: Long, hints: Int = 0, at: Long = 1_000L, isRecord: Boolean = true) =
        CompletionRecord(
            puzzleRef = TestGames.Ref(game, key),
            time = time,
            hints = hints,
            stats = RecordStats(schemaVersion = 1, blob = "{}"),
            firstCompletedAt = Instant.ofEpochMilli(at),
            isRecord = isRecord,
        )

    private suspend fun friend() = received.upsert(
        ReceivedPuzzle(
            code = friendCode, gameId = alpha, variant = TestGames.variant(alpha), sharedFormatVersion = 1,
            puzzleBytes = byteArrayOf(TestGames.MAGIC), title = "t", authorName = "bo", note = "",
            authorTimeMs = 1_000, authorHints = 0, authorUid = "bo", openedAt = Instant.EPOCH,
            unpublished = false, postState = PostState.Pending, postAttempts = 0, difficulty = null,
        ),
    )

    private suspend fun fetch(code: ShareCode): Match = (self.observe(code).first() as MatchFetch.Found).match

    private suspend fun stored(outcome: CreateOutcome): Match = fetch((outcome as CreateOutcome.Created).code)

    private suspend fun pastOf(match: Match): List<LegResult> =
        match.legs.indices.map { (self.readResult(match.code, it, SelfSeats.PAST) as ResultRead.Found).result }

    private fun settings(lineup: List<LineupEntry>?, friends: Boolean = false) =
        SelfDuelSettings(rounds = 3, games = setOf(alpha, beta), includeFriends = friends, lineup = lineup)

    @Test
    fun `a lineup duel is stored with the same fields as a random one, apart from its code and legs`() = runTest {
        completions.setRecords((1..3).map { record(alpha, daily(it), time = it * 10_000L, at = it * 1_000L) })
        for (friends in listOf(false, true)) {
            val random = stored(create()(settings(lineup = null, friends = friends)))
            val lined = stored(create()(settings(lineup = List(3) { AnyGame(alpha) }, friends = friends)))

            assertEquals(random.copy(code = lined.code, legs = lined.legs), lined)
            assertEquals(friends, lined.includeFriends)
            assertEquals(SelfSeats.NO_DEADLINE_HOURS, lined.deadlineHours)
            assertEquals(setOf(0, 1, 2), lined.progress[SelfSeats.PAST])
            assertTrue(lined.progress[SelfSeats.ME].orEmpty().isEmpty())
        }
    }

    @Test
    fun `each leg freezes its own first solve, in the lineup's order, an exact friend puzzle among them`() = runTest {
        friend()
        completions.setRecords(
            listOf(
                record(alpha, PuzzleKey.Custom(friendCode.value), time = 61_000, hints = 2, at = 3_000),
                record(alpha, PuzzleKey.Custom(friendCode.value), time = 5_000, at = 9_000, isRecord = false),
                record(beta, daily(4), time = 44_000, hints = 1, at = 2_000),
                record(alpha, daily(1), time = 27_000, at = 1_000),
            ),
        )
        val exact = ExactPuzzle(LegRef.Custom(alpha, friendCode), "bo", "t", "bo", PuzzleSource.Friend)

        val match = stored(create()(settings(lineup = listOf(exact, AnyGame(beta), AnyGame(alpha)))))

        assertEquals(listOf(LegRef.Custom(alpha, friendCode), LegRef.Daily(beta, LocalDate.of(2026, 11, 4), 4), LegRef.Daily(alpha, LocalDate.of(2026, 11, 1), 1)), match.legs.map { it.ref })
        assertEquals(listOf("bo", null, null), match.legs.map { it.authorUid })
        val past = pastOf(match)
        assertEquals(listOf(61_000L, 44_000L, 27_000L), past.map { it.timeMs })
        assertEquals(listOf(2, 1, 0), past.map { it.hints })
        assertEquals(listOf(3_000L, 2_000L, 1_000L), past.map { it.completedAtMs })
        assertTrue(past.all { it.uid == SelfSeats.PAST && !it.gaveUp })
    }

    @Test
    fun `a lineup duel's reveal waits for every leg of mine and then pairs each leg with its own frozen result`() = runTest {
        completions.setRecords(listOf(record(beta, daily(4), time = 90_000), record(alpha, daily(1), time = 20_000)))
        val match = stored(create()(settings(lineup = listOf(AnyGame(beta), AnyGame(alpha)))))
        val code = match.code
        suspend fun posted(leg: Int, timeMs: Long) {
            val mine = LegResult(SelfSeats.ME, leg, timeMs, hints = 0, completedAtMs = 9_000, gaveUp = false)
            outbox.enqueue(code, mine)
            self.post(code, mine)
            outbox.update(code, leg, SelfSeats.ME, ResultPostState.Posted, attempts = 0)
        }
        suspend fun ready() = fetch(code).revealReady(SelfSeats.ME, outbox.observe(code).first(), now)

        assertFalse(ready())
        posted(0, 50_000)
        assertFalse(ready())
        posted(1, 50_000)
        assertTrue(ready())

        val mine = outbox.observe(code).first().map { it.result }
        val outcome = ComputeMatchOutcome(match, mine + pastOf(match), SelfSeats.ME, now) { RankOrder.TimeThenHints }
        assertEquals(listOf(LegVerdict.Win(LegBasis.Time), LegVerdict.Loss(LegBasis.Time)), outcome.legs)
        assertEquals(MatchVerdict.Drawn, outcome.verdict)
    }
}
