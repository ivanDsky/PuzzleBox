package com.ivandsky.puzzlebox.compete.domain

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.LegResult
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.data.match.MatchFetch
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.ResultPostState
import com.ivandsky.puzzlebox.data.match.ResultRead
import com.ivandsky.puzzlebox.data.match.SelfSeats
import com.ivandsky.puzzlebox.domain.ComputeMatchOutcome
import com.ivandsky.puzzlebox.domain.LegReplayRead
import com.ivandsky.puzzlebox.domain.LoadLegReplay
import com.ivandsky.puzzlebox.domain.LoadSharedPuzzle
import com.ivandsky.puzzlebox.domain.MatchVerdict
import com.ivandsky.puzzlebox.domain.Outcome
import com.ivandsky.puzzlebox.domain.SolveReplayRef
import com.ivandsky.puzzlebox.domain.buildSelfMatch
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.model.SolveReplay
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.replay.ReplayFixtures
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeMatchResultOutbox
import com.ivandsky.puzzlebox.testing.repository.FakePublishedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSelfMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reveal of a self duel (two legs against my first solves), signed in or out: ready only after all my legs, the
 * unchanged outcome rule, and the compare replay of past you read through the self seat.
 */
class SelfDuelRevealTest {

    private val game = ReplayFixtures.GAME
    private val code = ShareCode.parse("K7Q2MX")!!
    private val created = Instant.ofEpochMilli(5_000_000)
    private val now = Instant.ofEpochMilli(9_000_000)
    private val firstSolve = ReplayFixtures.sealed(ReplayFixtures.SHORT_MOVES)

    private val daily = FakeDailyRepository()
    private val received = FakeReceivedPuzzleRepository()
    private val outbox = FakeMatchResultOutbox()
    private val identity = FakeIdentityService()
    private val self = FakeSelfMatchRepository(recordReplay = { ref ->
        // Only the first leg's first solve has a replay.
        firstSolve.takeIf { (ref as LegRef.Daily).number == 1 }
    })
    private val load = LoadLegReplay(
        matches = self,
        outbox = outbox,
        identity = identity,
        daily = daily,
        received = received,
        loadShared = LoadSharedPuzzle(
            registry = GameRegistry(emptyList()),
            shared = FakeSharedPuzzleRepository(),
            received = received,
            budget = { Budget.within(1_000, System::nanoTime) },
            clock = { Instant.EPOCH },
        ),
        published = FakePublishedPuzzleRepository(),
    )

    private fun day(index: Int) = LocalDate.of(2026, 11, 1 + index)

    /** Past me solved leg 0 in 41 s and leg 1 in 52 s. */
    private suspend fun aSelfDuelIsSaved() {
        val legs = List(2) { MatchLeg(LegRef.Daily(game, day(it), it + 1), authorUid = null) }
        val records = List(2) {
            CompletionRecord(
                puzzleRef = TestGames.Ref(game, PuzzleKey.Daily(day(it), it + 1)),
                time = 41_000L + it * 11_000L,
                hints = 0,
                stats = RecordStats(schemaVersion = 1, blob = "{}"),
                firstCompletedAt = Instant.ofEpochMilli(1_000),
                isRecord = true,
            )
        }
        val draft = buildSelfMatch(code, created, includeFriends = false, legs = legs, records = records)
        self.save(draft.match, draft.past)
        repeat(2) { daily.setCached(game, day(it), Puzzle(game, VariantId("fixture/v1"), 1, byteArrayOf(it.toByte()))) }
    }

    private fun mine(leg: Int, timeMs: Long, hints: Int = 0, gaveUp: Boolean = false) =
        LegResult(SelfSeats.ME, leg, if (gaveUp) 0 else timeMs, hints, 9_000, gaveUp)

    /** My result as the poster leaves it: outbox row Posted, self store holding it. */
    private suspend fun iPosted(result: LegResult, replay: SolveReplay? = null) {
        outbox.enqueue(code, result.copy(replay = replay))
        self.post(code, result)
        outbox.update(code, result.legIndex, SelfSeats.ME, ResultPostState.Posted, attempts = 0)
    }

    private suspend fun match(): Match = (self.observe(code).first() as MatchFetch.Found).match

    private suspend fun ready() = match().revealReady(SelfSeats.ME, outbox.observe(code).first(), now)

    private suspend fun outcome(): Outcome {
        val results = outbox.observe(code).first().map { it.result } +
            listOf(0, 1).map { (self.readResult(code, it, SelfSeats.PAST) as ResultRead.Found).result }
        return ComputeMatchOutcome(match(), results, SelfSeats.ME, now) { RankOrder.TimeThenHints }
    }

    @Test
    fun `reveal is ready only after every leg of mine is posted, signed out`() = runTest {
        aSelfDuelIsSaved()
        identity.currentIdentity = null

        assertFalse(ready())
        iPosted(mine(0, 30_000))
        assertFalse(ready())
        iPosted(mine(1, 30_000))
        assertTrue(ready())
    }

    @Test
    fun `a pending outbox row does not make the reveal ready`() = runTest {
        aSelfDuelIsSaved()
        iPosted(mine(0, 30_000))
        outbox.enqueue(code, mine(1, 30_000))
        self.post(code, mine(1, 30_000))

        assertFalse(ready())
    }

    @Test
    fun `beating both first solves wins the duel`() = runTest {
        aSelfDuelIsSaved()
        iPosted(mine(0, 30_000))
        iPosted(mine(1, 40_000))

        val result = outcome()
        assertEquals(MatchVerdict.Won, result.verdict)
        assertEquals(2, result.myWins)
    }

    @Test
    fun `slower than both first solves loses the duel`() = runTest {
        aSelfDuelIsSaved()
        iPosted(mine(0, 90_000))
        iPosted(mine(1, 90_000))

        assertEquals(MatchVerdict.Lost, outcome().verdict)
    }

    @Test
    fun `one win and one loss tie the duel`() = runTest {
        aSelfDuelIsSaved()
        iPosted(mine(0, 30_000))
        iPosted(mine(1, 90_000))

        assertEquals(MatchVerdict.Drawn, outcome().verdict)
    }

    @Test
    fun `giving up both legs loses the duel`() = runTest {
        aSelfDuelIsSaved()
        iPosted(mine(0, 0, gaveUp = true))
        iPosted(mine(1, 0, gaveUp = true))

        assertEquals(MatchVerdict.Lost, outcome().verdict)
    }

    @Test
    fun `signed out, the compare replay of past you is the first solve`() = runTest {
        aSelfDuelIsSaved()
        identity.currentIdentity = null
        iPosted(mine(0, 30_000))

        val source = load(SolveReplayRef.TheirLeg(code, 0))

        assertEquals(firstSolve, source?.bytes)
        assertEquals(game, source?.puzzle?.gameId)
    }

    @Test
    fun `signed in, the self seat still resolves rather than the account`() = runTest {
        aSelfDuelIsSaved()
        identity.currentIdentity = Identity("ann", "Ann", null)
        iPosted(mine(0, 30_000))

        assertEquals(firstSolve, load(SolveReplayRef.TheirLeg(code, 0))?.bytes)
    }

    @Test
    fun `a first solve without a replay gives no past you leg`() = runTest {
        aSelfDuelIsSaved()
        identity.currentIdentity = null
        iPosted(mine(1, 30_000))

        assertEquals(LegReplayRead.Absent, load.read(SolveReplayRef.TheirLeg(code, 1)))
    }

    @Test
    fun `my own leg replay opens from the outbox signed out`() = runTest {
        aSelfDuelIsSaved()
        identity.currentIdentity = null
        val mineReplay = ReplayFixtures.sealed(ReplayFixtures.SHORT_MOVES + 1)
        iPosted(mine(0, 30_000), replay = mineReplay)

        assertEquals(mineReplay, load(SolveReplayRef.MyLeg(code, 0))?.bytes)
    }

    @Test
    fun `a self duel that is not held reads as no replay signed out`() = runTest {
        identity.currentIdentity = null

        assertNull(load(SolveReplayRef.TheirLeg(code, 0)))
        assertTrue(self.observe(code).first() is MatchFetch.NotFound)
    }
}
