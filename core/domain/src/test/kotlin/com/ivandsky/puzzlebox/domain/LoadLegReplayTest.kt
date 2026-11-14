package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.LegResult
import com.ivandsky.puzzlebox.data.match.MatchDraft
import com.ivandsky.puzzlebox.data.match.MatchFetch
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.MatchRepository
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.data.match.ResultRead
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.domain.SolveReplayRef.MyLeg
import com.ivandsky.puzzlebox.domain.SolveReplayRef.Record
import com.ivandsky.puzzlebox.domain.SolveReplayRef.TheirLeg
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.GameRules
import com.ivandsky.puzzlebox.game.GameSessionCodec
import com.ivandsky.puzzlebox.game.Hint
import com.ivandsky.puzzlebox.game.Move
import com.ivandsky.puzzlebox.game.MoveVerdict
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.ScoreModel
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.replay.MoveLog
import com.ivandsky.puzzlebox.game.replay.ReplayMove
import com.ivandsky.puzzlebox.game.replay.ReplayOutcome
import com.ivandsky.puzzlebox.game.replay.Timeline
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.model.SolveReplay
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.replay.FixtureSession
import com.ivandsky.puzzlebox.testing.replay.ReplayFixtures
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyReplayRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchResultOutbox
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import java.time.Instant
import java.time.LocalDate
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** The leg resolver behind the replay entry: my leg from the outbox, their leg from their posted result. */
class LoadLegReplayTest {

    private val game = ReplayFixtures.GAME
    private val code = ShareCode.parse("K7Q2MX")!!
    private val friendCode = ShareCode.parse("ABC234")!!
    private val ann = Player("ann", "Ann", null)
    private val bob = Player("bob", "Bob", null)
    private val solvedDay = LocalDate.of(2026, 11, 9)
    private val gaveUpDay = LocalDate.of(2026, 11, 10)

    private val server = FakeMatchRepository()
    private val matches = CachingMatchRepository(server)
    private val outbox = FakeMatchResultOutbox()
    private val identity = FakeIdentityService()
    private val daily = FakeDailyRepository()
    private val received = FakeReceivedPuzzleRepository()
    private val loadShared = LoadSharedPuzzle(
        registry = GameRegistry(emptyList()),
        shared = FakeSharedPuzzleRepository(),
        received = received,
        budget = { Budget.within(1_000, System::nanoTime) },
        clock = { Instant.EPOCH },
    )
    private val load = resolver(daily)

    private val solved = ReplayFixtures.sealed(ReplayFixtures.SHORT_MOVES)
    private val gaveUp = ReplayFixtures.sealed(ReplayFixtures.SHORT_MOVES, outcome = ReplayOutcome.GaveUp)

    private fun result(uid: String, leg: Int, replay: SolveReplay?, gaveUp: Boolean = false) = LegResult(
        uid = uid,
        legIndex = leg,
        timeMs = if (gaveUp) 0 else 12_000,
        hints = 0,
        completedAtMs = 1_000,
        gaveUp = gaveUp,
        replay = replay,
    )

    /** Ann challenges Bob over a solved-day leg 0 and a gave-up-day leg 1; the viewer is Ann. */
    private suspend fun duel(legs: List<MatchLeg> = dailyLegs(), withOpponent: Boolean = true) {
        daily.setCached(game, solvedDay, puzzle(ReplayFixtures.SHORT_MOVES))
        daily.setCached(game, gaveUpDay, puzzle(ReplayFixtures.SHORT_MOVES + 1))
        server.signedInUid = ann.uid
        server.create(MatchDraft(code, 24, ann, null, legs, includeFriends = false, rematchOf = null))
        if (withOpponent) {
            server.signedInUid = bob.uid
            server.claim(code, bob)
        }
        viewAs(ann)
    }

    private fun dailyLegs() = listOf(
        MatchLeg(LegRef.Daily(game, solvedDay, 1), null),
        MatchLeg(LegRef.Daily(game, gaveUpDay, 2), null),
    )

    private fun viewAs(player: Player) {
        identity.currentIdentity = Identity(player.uid, player.name, null)
        server.signedInUid = player.uid
    }

    private suspend fun post(player: Player, result: LegResult) {
        server.signedInUid = player.uid
        server.post(code, result)
        viewAs(ann)
    }

    @Test
    fun `my leg opens from the outbox row`() = runTest {
        duel()
        outbox.enqueue(code, result(ann.uid, 0, solved))

        val source = load(MyLeg(code, 0))

        assertEquals(solved, source?.bytes)
        assertEquals(game, source?.puzzle?.gameId)
        assertNotNull(entryTimeline(MyLeg(code, 0)))
    }

    @Test
    fun `my leg opens before the opponent has joined`() = runTest {
        duel(withOpponent = false)
        outbox.enqueue(code, result(ann.uid, 0, solved))

        assertEquals(solved, load(MyLeg(code, 0))?.bytes)
        assertEquals(game, load(MyLeg(code, 0))?.puzzle?.gameId)
    }

    @Test
    fun `my leg without a row or without a replay is null`() = runTest {
        duel()
        outbox.enqueue(code, result(ann.uid, 1, replay = null))

        assertNull(load(MyLeg(code, 0)))
        assertNull(load(MyLeg(code, 1)))
    }

    @Test
    fun `my leg ignores a row of another player`() = runTest {
        duel()
        outbox.enqueue(code, result(bob.uid, 0, solved))

        assertNull(load(MyLeg(code, 0)))
    }

    @Test
    fun `their leg opens from the posted result`() = runTest {
        duel()
        post(bob, result(bob.uid, 0, solved))
        post(ann, result(ann.uid, 0, replay = null))

        val source = load(TheirLeg(code, 0))

        assertEquals(solved, source?.bytes)
        assertNotNull(entryTimeline(TheirLeg(code, 0)))
    }

    @Test
    fun `their result without a replay is null`() = runTest {
        duel()
        post(bob, result(bob.uid, 0, replay = null))
        post(ann, result(ann.uid, 0, replay = null))

        assertNull(load(TheirLeg(code, 0)))
    }

    @Test
    fun `their leg is null while their result is sealed or missing`() = runTest {
        duel()
        post(bob, result(bob.uid, 0, solved))

        assertNull(load(TheirLeg(code, 0)))
        assertNull(load(TheirLeg(code, 1)))
    }

    @Test
    fun `their leg is null offline`() = runTest {
        duel()
        post(bob, result(bob.uid, 0, solved))
        post(ann, result(ann.uid, 0, replay = null))
        server.online = false

        assertNull(load(TheirLeg(code, 0)))
    }

    @Test
    fun `their leg is null before an opponent has joined`() = runTest {
        duel(withOpponent = false)

        assertNull(load(TheirLeg(code, 0)))
    }

    @Test
    fun `their replay of another game does not open`() = runTest {
        duel()
        post(bob, result(bob.uid, 0, otherGame()))
        post(ann, result(ann.uid, 0, replay = null))

        assertNotNull(load(TheirLeg(code, 0)))
        assertNull(entryTimeline(TheirLeg(code, 0)))
    }

    @Test
    fun `my leg opens offline from a match this device has seen`() = runTest {
        duel()
        outbox.enqueue(code, result(ann.uid, 0, solved))
        matches.observe(code).first()
        server.online = false

        assertEquals(solved, load(MyLeg(code, 0))?.bytes)
    }

    @Test
    fun `their gave-up replay opens unsolved`() = runTest {
        duel()
        post(bob, result(bob.uid, 1, gaveUp, gaveUp = true))
        post(ann, result(ann.uid, 1, replay = null))

        assertEquals(ReplayOutcome.GaveUp, entryTimeline(TheirLeg(code, 1))?.outcome)
    }

    @Test
    fun `cancellation propagates`() = runTest {
        duel()
        outbox.enqueue(code, result(ann.uid, 0, solved))
        matches.failure = CancellationException("cancelled")

        val thrown = try {
            load(MyLeg(code, 0))
            null
        } catch (e: CancellationException) {
            e
        }

        assertNotNull(thrown)
    }

    @Test
    fun `a gave-up replay opens unsolved`() = runTest {
        duel()
        outbox.enqueue(code, result(ann.uid, 1, gaveUp, gaveUp = true))

        val timeline = entryTimeline(MyLeg(code, 1))

        assertEquals(ReplayOutcome.GaveUp, timeline?.outcome)
    }

    @Test
    fun `a custom leg takes its puzzle from the received store`() = runTest {
        received.upsert(receivedRow())
        duel(listOf(MatchLeg(LegRef.Custom(game, friendCode), "bob")))
        outbox.enqueue(code, result(ann.uid, 0, solved))

        val source = load(MyLeg(code, 0))

        assertEquals(solved, source?.bytes)
        assertEquals(ReplayFixtures.SHORT_MOVES.toByte(), source?.puzzle?.payload?.single())
    }

    @Test
    fun `a custom leg of another game is null`() = runTest {
        received.upsert(receivedRow().copy(gameId = TestGames.alpha))
        duel(listOf(MatchLeg(LegRef.Custom(game, friendCode), "bob")))
        outbox.enqueue(code, result(ann.uid, 0, solved))

        assertNull(load(MyLeg(code, 0)))
    }

    @Test
    fun `a leg whose puzzle is not on this device is null`() = runTest {
        duel()
        val bare = resolver(FakeDailyRepository())
        outbox.enqueue(code, result(ann.uid, 0, solved))

        assertNull(bare(MyLeg(code, 0)))
    }

    @Test
    fun `a signed-out viewer gets null`() = runTest {
        duel()
        outbox.enqueue(code, result(ann.uid, 0, solved))
        identity.currentIdentity = null

        assertNull(load(MyLeg(code, 0)))
    }

    @Test
    fun `a puzzle reference gives null without a read`() = runTest {
        duel()
        matches.reads = 0

        assertNull(load(Record(TestGames.Ref(game, PuzzleKey.Daily(solvedDay, 1)))))
        assertEquals(0, matches.reads)
    }

    private fun puzzle(total: Int) =
        Puzzle(game, VariantId("fixture/v1"), 1, byteArrayOf(total.toByte()))

    private fun receivedRow() = ReceivedPuzzle(
        code = friendCode,
        gameId = game,
        variant = VariantId("fixture/v1"),
        sharedFormatVersion = 1,
        puzzleBytes = byteArrayOf(ReplayFixtures.SHORT_MOVES.toByte()),
        title = "t",
        authorName = "Bob",
        note = "",
        authorTimeMs = 0,
        authorHints = 0,
        authorUid = bob.uid,
        openedAt = Instant.EPOCH,
        unpublished = false,
        postState = PostState.Pending,
        postAttempts = 0,
        difficulty = null,
    )

    /** One legal move in a replay recorded for a game this build has no plugin for. */
    private fun otherGame(): SolveReplay {
        val log = MoveLog.fresh(GameId("other"))
        log.play(ReplayFixtures.replay, ReplayFixtures.initial(1), ReplayMove(0, IntArray(0)), 1_000)
        return checkNotNull(log.seal(ReplayOutcome.Solved, endMs = 1_000))
    }

    private suspend fun entryTimeline(ref: SolveReplayRef) = entry()(ref)

    private fun resolver(dailyRepo: FakeDailyRepository) =
        LoadLegReplay(matches, outbox, identity, dailyRepo, received, loadShared)

    private fun entry(): suspend (SolveReplayRef) -> Timeline? {
        val completions = FakeCompletionRepository()
        val solve = LoadSolveReplay(
            games = GameRegistry(listOf(fixturePlugin())),
            daily = LoadDailyReplay(completions, daily),
            custom = LoadCustomReplay(completions, received, loadShared),
            leg = load,
            player = LoadPlayerReplay(FakeDailyReplayRepository(), daily),
            completions = completions,
            dispatchers = InlineDispatcherProvider,
        )
        return { ref -> solve(ref) }
    }

    /** The fixture game's plugin; a puzzle's one payload byte is its piece count. */
    private fun fixturePlugin(): GamePlugin<Unit, FixtureSession, TestGames.Model> = GamePlugin(
        definition = TestGames.definition(game),
        rules = object : GameRules<Unit> {
            override fun validate(move: Move, state: Unit): MoveVerdict = object : MoveVerdict {}
            override fun isComplete(state: Unit) = false
            override fun hint(state: Unit): Hint = object : Hint {}
        },
        session = object : GameSessionCodec<FixtureSession> {
            override fun serialize(session: FixtureSession) = ByteArray(0)
            override fun deserialize(bytes: ByteArray): DecodeResult<FixtureSession> =
                DecodeResult.Ok(ReplayFixtures.initial(0))
        },
        score = object : ScoreModel<FixtureSession> {
            override fun completion(session: FixtureSession): CompletionRecord? = null
        },
        stateOf = { },
        initialSession = { DecodeResult.Ok(ReplayFixtures.initial(it.payload.single().toInt())) },
        refOf = { TestGames.Ref(game, it) },
        playAgainSession = { _, _ -> DecodeResult.Ok(ByteArray(0)) },
        rendererModel = { _, _, _ -> TestGames.Model },
        replay = ReplayFixtures.replay,
    )

    /**
     * Counts every read of the match store, and serves the last match it saw while the store reads Offline, as the
     * Firestore adapter does from its cache (FirestoreMatchStore.toMatchSnapshot). [failure] is thrown by the next read.
     */
    private class CachingMatchRepository(private val inner: MatchRepository) : MatchRepository by inner {
        var reads = 0
        var failure: Throwable? = null
        private val seen = HashMap<ShareCode, MatchFetch.Found>()

        override fun observe(code: ShareCode): Flow<MatchFetch> {
            reads++
            failure?.let { throw it }
            return inner.observe(code).map { fetch ->
                when {
                    fetch is MatchFetch.Found -> fetch.also { seen[code] = it }
                    fetch == MatchFetch.Offline -> seen[code] ?: fetch
                    else -> fetch
                }
            }
        }

        override suspend fun readResult(code: ShareCode, legIndex: Int, uid: String): ResultRead {
            reads++
            return inner.readResult(code, legIndex, uid)
        }
    }

    private object InlineDispatcherProvider : DispatcherProvider {
        private val inline = object : CoroutineDispatcher() {
            override fun isDispatchNeeded(context: CoroutineContext) = false
            override fun dispatch(context: CoroutineContext, block: Runnable) = block.run()
        }
        override val main: CoroutineDispatcher = inline
        override val io: CoroutineDispatcher = inline
        override val default: CoroutineDispatcher = inline
    }
}
