package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.domain.SolveReplayRef.Latest
import com.ivandsky.puzzlebox.domain.SolveReplayRef.MyLeg
import com.ivandsky.puzzlebox.domain.SolveReplayRef.Record
import com.ivandsky.puzzlebox.domain.SolveReplayRef.TheirLeg
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.GameRules
import com.ivandsky.puzzlebox.game.GameSessionCodec
import com.ivandsky.puzzlebox.game.Hint
import com.ivandsky.puzzlebox.game.Move
import com.ivandsky.puzzlebox.game.MoveVerdict
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.game.ScoreModel
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.DraftExport
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorShareCodec
import com.ivandsky.puzzlebox.game.editor.GameEditor
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.game.replay.Timeline
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.model.SolveReplay
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyReplayRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchResultOutbox
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import com.ivandsky.puzzlebox.testing.replay.FixtureSession
import com.ivandsky.puzzlebox.testing.replay.ReplayFixtures
import java.time.Instant
import java.time.LocalDate
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The custom resolver (spec A13, D8 slice 7) through [LoadSolveReplay] over the fake stores. The game
 * is the replay fixture game, so a resolved source opens a real timeline. Every outcome is asserted on
 * the [Timeline] it opens to. Only Record and Latest of a custom puzzle read anything.
 */
class LoadCustomReplayTest {

    private val inner = FakeCompletionRepository()
    private val completions = CountingCompletionRepository(inner)
    private val received = FakeReceivedPuzzleRepository()
    private val shared = FakeSharedPuzzleRepository().apply { signedInUid = AUTHOR }
    private val registry = GameRegistry(listOf(fixturePlugin(), fixturePlugin(TestGames.beta)))
    private val loadShared = LoadSharedPuzzle(
        registry = registry,
        shared = shared,
        received = received,
        budget = { Budget.within(BUDGET_MS, System::nanoTime) },
        clock = { NOW },
    )
    private val custom = LoadCustomReplay(completions, received, loadShared)
    private val ref = TestGames.Ref(FIXTURE, PuzzleKey.Custom(CODE.value))

    @Test
    fun `a received custom puzzle opens its record solve`() = runTest {
        received.upsert(stored())
        solved(ReplayFixtures.sealed(ReplayFixtures.SHORT_MOVES), isRecord = true)

        assertSameTimeline(ReplayFixtures.timeline(ReplayFixtures.SHORT_MOVES), resolve(Record(ref)))
    }

    @Test
    fun `a received custom puzzle opens its newest solve, which is not the record`() = runTest {
        received.upsert(stored())
        solved(ReplayFixtures.sealed(ReplayFixtures.SHORT_MOVES), isRecord = true)
        solved(ReplayFixtures.sealed(ReplayFixtures.SHORT_MOVES, stepMs = 2_000), isRecord = false)

        assertSameTimeline(ReplayFixtures.timeline(ReplayFixtures.SHORT_MOVES), resolve(Record(ref)))
        assertSameTimeline(ReplayFixtures.timeline(ReplayFixtures.SHORT_MOVES, stepMs = 2_000), resolve(Latest(ref)))
    }

    @Test
    fun `an own-created puzzle opens from its shared document`() = runTest {
        shared.publish(doc())
        solved(ReplayFixtures.sealed(ReplayFixtures.SHORT_MOVES), isRecord = true)

        assertSameTimeline(ReplayFixtures.timeline(ReplayFixtures.SHORT_MOVES), resolve(Record(ref)))
        assertTrue(received.rows.containsKey(CODE))
    }

    @Test
    fun `the store wins over the shared document when both hold the puzzle`() = runTest {
        received.upsert(stored())
        // The shared copy names another game, so opening it would fail. Only the store makes this open.
        shared.publish(doc().copy(gameId = TestGames.beta.value))
        solved(ReplayFixtures.sealed(ReplayFixtures.SHORT_MOVES), isRecord = true)

        assertSameTimeline(ReplayFixtures.timeline(ReplayFixtures.SHORT_MOVES), resolve(Record(ref)))
    }

    @Test
    fun `an unknown code gives null even with a stored solve`() = runTest {
        solved(ReplayFixtures.sealed(ReplayFixtures.SHORT_MOVES), isRecord = true)

        assertNull(resolve(Record(ref)))
    }

    @Test
    fun `a custom solve with no stored bytes gives null and loads nothing`() = runTest {
        shared.publish(doc())

        assertNull(resolve(Record(ref)))
        assertTrue(received.rows.isEmpty())
    }

    @Test
    fun `a puzzle of another game than the solve's gives null`() = runTest {
        // The beta plugin is registered and the bytes are the fixture game's, so the opener refuses them.
        received.upsert(stored(gameId = TestGames.beta))
        solved(ReplayFixtures.sealed(ReplayFixtures.SHORT_MOVES), isRecord = true)

        assertNull(resolve(Record(ref)))
    }

    @Test
    fun `a daily reference gives null without a custom read`() = runTest {
        received.upsert(stored())
        solved(ReplayFixtures.sealed(ReplayFixtures.SHORT_MOVES), isRecord = true)
        val daily = TestGames.Ref(FIXTURE, PuzzleKey.Daily(LocalDate.parse("2026-11-08"), 1))

        assertNull(custom(Record(daily)))
        assertNull(custom(Latest(daily)))
        assertEquals(0, completions.reads)
        assertNull(resolve(Record(daily)))
    }

    @Test
    fun `a leg reference gives null without a read`() = runTest {
        received.upsert(stored())
        solved(ReplayFixtures.sealed(ReplayFixtures.SHORT_MOVES), isRecord = true)

        assertNull(resolve(MyLeg(CODE, 0)))
        assertNull(resolve(TheirLeg(CODE, 0)))
        assertEquals(0, completions.reads)
    }

    @Test
    fun `a failing store read gives null`() = runTest {
        received.upsert(stored())
        completions.throwOnRead = true

        assertNull(resolve(Record(ref)))
        assertEquals(listOf("disk full"), logged.map { it.message })
    }

    @Test
    fun `cancellation during the store read propagates`() = runTest {
        received.upsert(stored())
        completions.cancelOnRead = true

        try {
            resolve(Record(ref))
            fail("cancellation must propagate, not become null")
        } catch (e: CancellationException) {
            assertEquals("cancelled", e.message)
        }
    }

    private suspend fun resolve(ref: SolveReplayRef): Timeline? =
        solveReplay(registry).invoke(ref)

    private fun assertSameTimeline(expected: Timeline, actual: Timeline?) {
        assertNotNull(actual)
        assertEquals(expected.game, actual!!.game)
        assertEquals(expected.outcome, actual.outcome)
        assertEquals(expected.endMs, actual.endMs)
        assertEquals(expected.moveTimesMs.toList(), actual.moveTimesMs.toList())
    }

    private suspend fun solved(replay: SolveReplay, isRecord: Boolean) = inner.onCompleted(
        CompletionRecord(
            puzzleRef = ref,
            time = 1_000,
            hints = 0,
            stats = RecordStats(schemaVersion = 1, blob = "{}"),
            isRecord = isRecord,
        ),
        replay,
    )

    private fun solveReplay(games: GameRegistry) = LoadSolveReplay(
        games = games,
        daily = LoadDailyReplay(completions, FakeDailyRepository()),
        custom = custom,
        leg = LoadLegReplay(
            matches = FakeMatchRepository(),
            outbox = FakeMatchResultOutbox(),
            identity = FakeIdentityService(),
            daily = FakeDailyRepository(),
            received = received,
            loadShared = loadShared,
        ),
        player = LoadPlayerReplay(FakeDailyReplayRepository(), FakeDailyRepository()),
        completions = completions,
        dispatchers = InlineDispatcherProvider,
        failures = { _, _, cause -> logged += cause },
    )

    private val logged = mutableListOf<Throwable>()

    private fun stored(gameId: GameId = FIXTURE) = ReceivedPuzzle(
        code = CODE, gameId = gameId, variant = PairEditor.VARIANT, sharedFormatVersion = 1,
        puzzleBytes = byteArrayOf(1, 2), title = "Twins", authorName = "Jonas", note = "", authorTimeMs = 42_000L,
        authorHints = 0, authorUid = AUTHOR, openedAt = NOW, unpublished = false,
        postState = PostState.Pending, postAttempts = 0, difficulty = null,
    )

    private fun doc() = SharedPuzzleDoc(
        code = CODE, formatVersion = SharedPuzzleDoc.FORMAT_VERSION, gameId = FIXTURE.value,
        variant = PairEditor.VARIANT.value, shareCodecVersion = PairEditor.SHARE_VERSION, payload = byteArrayOf(1, 0),
        title = "Twins", authorName = "Jonas", note = "", authorUid = AUTHOR, authorTimeMs = 42_000L, authorHints = 0,
        createdAtMs = 0L, unpublished = false,
    )

    /** [LoadSolveReplay]'s store reads, counted, with injectable failure and cancellation. */
    private class CountingCompletionRepository(private val inner: FakeCompletionRepository) : CompletionRepository by inner {
        var reads = 0
        var throwOnRead = false
        var cancelOnRead = false

        private fun read() {
            reads++
            if (cancelOnRead) throw CancellationException("cancelled")
            if (throwOnRead) throw IllegalStateException("disk full")
        }

        override suspend fun recordReplay(puzzleRef: PuzzleRef): SolveReplay? {
            read()
            return inner.recordReplay(puzzleRef)
        }

        override suspend fun latestReplay(puzzleRef: PuzzleRef): SolveReplay? {
            read()
            return inner.latestReplay(puzzleRef)
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

    /**
     * Two cells, each 1 or 2 and never equal; `[1, 0]` has one solution, `[0, 0]` two. Copied from
     * LoadSharedPuzzleTest so an own-created puzzle can verify on open.
     */
    private object PairEditor : GameEditor<List<Int>>, EditorShareCodec<List<Int>>, DraftExport<List<Int>> {
        const val SHARE_VERSION = 1
        val VARIANT = VariantId("pair/n2")

        override val gameId: GameId = FIXTURE
        override val variants: List<VariantId> = listOf(VARIANT)
        override val draftFormatVersion: Int = 1
        override val shareFormatVersion: Int = SHARE_VERSION
        override fun newDraft(variant: VariantId): List<Int> = listOf(0, 0)
        override fun variantOf(draft: List<Int>): VariantId = VARIANT
        override fun encodeDraft(state: DraftState<List<Int>>): ByteArray = encodeShare(state.draft)
        override fun decodeDraft(bytes: ByteArray): DecodeResult<DraftState<List<Int>>> = when (val d = decodeShare(bytes)) {
            is DecodeResult.Ok -> DecodeResult.Ok(DraftState(d.value))
            is DecodeResult.Err -> d
        }

        override fun analyze(draft: List<Int>, budget: Budget): Verdict {
            val solutions = listOf(listOf(1, 2), listOf(2, 1))
                .filter { candidate -> draft.indices.all { draft[it] == 0 || draft[it] == candidate[it] } }
                .map(::Solution)
            return when (solutions.size) {
                0 -> Verdict.None(conflicts = draft.indices.toSet())
                1 -> Verdict.Unique(solutions.single())
                else -> Verdict.Multiple(solutions[0], solutions[1], differingCells = draft.indices.toSet())
            }
        }

        override fun encodeShare(draft: List<Int>): ByteArray = ByteArray(draft.size) { draft[it].toByte() }
        override fun decodeShare(bytes: ByteArray): DecodeResult<List<Int>> =
            if (bytes.size == 2 && bytes.all { it in 0..2 }) DecodeResult.Ok(bytes.map { it.toInt() })
            else DecodeResult.Err(DecodeError.Malformed("not a pair board"))

        override fun toPuzzle(draft: List<Int>, solution: Solution): Puzzle =
            Puzzle(gameId, VARIANT, 1, ByteArray(solution.values.size) { solution.values[it].toByte() })
    }

    private companion object {
        val FIXTURE: GameId = ReplayFixtures.GAME
        val CODE = checkNotNull(ShareCode.parse("K7Q2MX"))
        const val AUTHOR = "uid-jonas"
        const val BUDGET_MS = 1_000L
        val NOW: Instant = Instant.parse("2026-11-07T10:00:00Z")

        /** The fixture game as a plugin with a replay member and the pair editor, so opens reach a real timeline. */
        fun fixturePlugin(game: GameId = FIXTURE): GamePlugin<Unit, FixtureSession, TestGames.Model> = GamePlugin(
            definition = TestGames.definition(game),
            rules = object : GameRules<Unit> {
                override fun validate(move: Move, state: Unit): MoveVerdict = object : MoveVerdict {}
                override fun isComplete(state: Unit): Boolean = false
                override fun hint(state: Unit): Hint = object : Hint {}
            },
            session = object : GameSessionCodec<FixtureSession> {
                override fun serialize(session: FixtureSession): ByteArray = ByteArray(0)
                override fun deserialize(bytes: ByteArray): DecodeResult<FixtureSession> =
                    DecodeResult.Ok(ReplayFixtures.initial(ReplayFixtures.SHORT_MOVES))
            },
            score = object : ScoreModel<FixtureSession> {
                override fun completion(session: FixtureSession): CompletionRecord? = null
            },
            stateOf = { },
            initialSession = { DecodeResult.Ok(ReplayFixtures.initial(ReplayFixtures.SHORT_MOVES)) },
            refOf = { key -> TestGames.Ref(game, key) },
            playAgainSession = { cached, _ -> DecodeResult.Ok(cached.payload) },
            rendererModel = { _, _, _ -> TestGames.Model },
            editor = PairEditor,
            replay = ReplayFixtures.replay,
        )
    }
}
