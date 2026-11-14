package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.common.log.FailureLog
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.domain.SolveReplayRef.Latest
import com.ivandsky.puzzlebox.domain.SolveReplayRef.Record
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.GameSessionCodec
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.game.ScoreModel
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.replay.ReplayOutcome
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.SolveReplay
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
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
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The daily resolver through the real entry: dispatch, cache-only puzzle read and the open (S10, S11). */
class LoadDailyReplayTest {

    private val game = ReplayFixtures.GAME
    private val day = LocalDate.parse("2026-11-08")
    private val puzzle = TestGames.Ref(game, PuzzleKey.Daily(day, 1))
    private val shortReplay = ReplayFixtures.sealed(ReplayFixtures.SHORT_MOVES)
    private val longReplay = ReplayFixtures.sealed(ReplayFixtures.SHORT_MOVES, stepMs = 2_000)
    private val cachedPuzzle = TestGames.puzzle(game)

    @Test
    fun `record opens the first solve and ends solved`() = runTest {
        val completions = twoSolves(first = shortReplay, second = longReplay)

        val timeline = checkNotNull(entry(completions = completions)(Record(puzzle)))

        assertEquals(ReplayOutcome.Solved, timeline.outcome)
        assertEquals(ReplayFixtures.SHORT_MOVES * 1_000L, timeline.endMs)
    }

    @Test
    fun `latest opens the newest solve and ends solved`() = runTest {
        val completions = twoSolves(first = shortReplay, second = longReplay)

        val timeline = checkNotNull(entry(completions = completions)(Latest(puzzle)))

        assertEquals(ReplayOutcome.Solved, timeline.outcome)
        assertEquals(ReplayFixtures.SHORT_MOVES * 2_000L, timeline.endMs)
    }

    @Test
    fun `no replay row gives null for record and latest`() = runTest {
        val completions = twoSolves(first = null, second = null)
        val load = entry(completions = completions)

        assertNull(load(Record(puzzle)))
        assertNull(load(Latest(puzzle)))
    }

    @Test
    fun `corrupt bytes give null`() = runTest {
        val corrupt = checkNotNull(SolveReplay.of(byteArrayOf(1, 2, 3)))
        val completions = twoSolves(first = corrupt, second = corrupt)
        val load = entry(completions = completions)

        assertNull(load(Record(puzzle)))
        assertNull(load(Latest(puzzle)))
    }

    @Test
    fun `bytes of another game give null`() = runTest {
        val completions = twoSolves(first = shortReplay, second = shortReplay)
        val load = entry(completions = completions, registered = GameId("other"))

        assertNull(load(Record(puzzle.copy(gameId = GameId("other")))))
        assertNull(load(Latest(puzzle.copy(gameId = GameId("other")))))
    }

    @Test
    fun `a day the cache dropped gives null`() = runTest {
        val completions = twoSolves(first = shortReplay, second = longReplay)
        val load = entry(completions = completions, cached = false)

        assertNull(load(Record(puzzle)))
        assertNull(load(Latest(puzzle)))
    }

    @Test
    fun `a custom reference gives null without a read`() = runTest {
        val custom = TestGames.Ref(game, PuzzleKey.Custom("abc"))
        val completions = ThrowingCompletionRepository(IllegalStateException("store down"))
        val load = LoadDailyReplay(completions, FakeDailyRepository())

        assertNull(load(Record(custom)))
        assertNull(load(Latest(custom)))
        assertEquals(0, completions.reads)
    }

    @Test
    fun `a failing read gives null`() = runTest {
        val logged = mutableListOf<Throwable>()
        val load = entry(
            completions = ThrowingCompletionRepository(IllegalStateException("store down")),
            failures = { _, _, cause -> logged += cause },
        )

        assertNull(load(Record(puzzle)))
        assertNull(load(Latest(puzzle)))
        assertEquals(listOf("store down", "store down"), logged.map { it.message })
    }

    @Test
    fun `cancellation propagates instead of becoming null`() = runTest {
        val load = entry(completions = ThrowingCompletionRepository(CancellationException("cancelled")))

        assertTrue(runCatching { load(Record(puzzle)) }.exceptionOrNull() is CancellationException)
        assertTrue(runCatching { load(Latest(puzzle)) }.exceptionOrNull() is CancellationException)
    }

    @Test
    fun `flag off stays null even with a stored replay`() = runTest {
        val completions = twoSolves(first = shortReplay, second = longReplay)

        assertNull(entry(FakeReleaseFlags(), completions)(Record(puzzle)))
    }

    @Test
    fun `latestIsRecord is false after a second solve`() = runTest {
        val completions = twoSolves(first = shortReplay, second = longReplay)
        val load = entry(completions = completions)

        assertFalse(load.latestIsRecord(puzzle))
    }

    @Test
    fun `latestIsRecord is true after a single solve`() = runTest {
        val completions = FakeCompletionRepository()
        completions.onCompleted(solve(isRecord = true), shortReplay)

        assertTrue(entry(completions = completions).latestIsRecord(puzzle))
    }

    @Test
    fun `the resolver returns the cached puzzle with the stored bytes`() = runTest {
        val completions = twoSolves(first = shortReplay, second = longReplay)
        val daily = FakeDailyRepository().apply { setCached(game, day, cachedPuzzle) }

        val source = checkNotNull(LoadDailyReplay(completions, daily)(Latest(puzzle)))

        assertEquals(cachedPuzzle, source.puzzle)
        assertEquals(longReplay, source.bytes)
    }

    private suspend fun twoSolves(first: SolveReplay?, second: SolveReplay?): FakeCompletionRepository {
        val completions = FakeCompletionRepository()
        completions.onCompleted(solve(isRecord = true), first)
        completions.onCompleted(solve(isRecord = false), second)
        return completions
    }

    private fun solve(isRecord: Boolean) = CompletionRecord(
        puzzleRef = puzzle,
        time = 1_000,
        hints = 0,
        stats = RecordStats(schemaVersion = 1, blob = "{}"),
        isRecord = isRecord,
    )

    private fun entry(
        flags: ReleaseFlags = FakeReleaseFlags(ReleaseFlag.FullReplay),
        completions: CompletionRepository = FakeCompletionRepository(),
        cached: Boolean = true,
        registered: GameId = game,
        failures: FailureLog = FailureLog { _, _, _ -> },
    ): LoadSolveReplay {
        val daily = FakeDailyRepository().apply {
            if (cached) setCached(registered, day, TestGames.puzzle(registered))
        }
        val received = FakeReceivedPuzzleRepository()
        val games = GameRegistry(listOf(fixturePlugin(registered)))
        val loadShared = LoadSharedPuzzle(
            registry = games,
            shared = FakeSharedPuzzleRepository(),
            received = received,
            budget = { Budget.within(1_000, System::nanoTime) },
            clock = { Instant.EPOCH },
        )
        return LoadSolveReplay(
            flags = flags,
            games = games,
            daily = LoadDailyReplay(completions, daily),
            custom = LoadCustomReplay(completions, received, loadShared),
            leg = LoadLegReplay(
                matches = FakeMatchRepository(),
                outbox = FakeMatchResultOutbox(),
                identity = FakeIdentityService(),
                daily = daily,
                received = received,
                loadShared = loadShared,
            ),
            player = LoadPlayerReplay(FakeDailyReplayRepository(), daily),
            completions = completions,
            dispatchers = InlineDispatcherProvider,
            failures = failures,
        )
    }

    /** The fixture game's replay under [id]; a plugin whose id differs from the bytes' game must refuse them. */
    private fun fixturePlugin(id: GameId): GamePlugin<Unit, FixtureSession, TestGames.Model> {
        val base = TestGames.plugin(id)
        return GamePlugin(
            definition = base.definition,
            rules = base.rules,
            session = fixtureCodec,
            score = fixtureScore,
            stateOf = { },
            initialSession = { _: Puzzle -> DecodeResult.Ok(ReplayFixtures.initial(ReplayFixtures.SHORT_MOVES)) },
            refOf = base.refOf,
            playAgainSession = base.playAgainSession,
            rendererModel = { _, _, _ -> TestGames.Model },
            replay = ReplayFixtures.replay,
        )
    }

    private val fixtureCodec = object : GameSessionCodec<FixtureSession> {
        override fun serialize(session: FixtureSession): ByteArray = ByteArray(0)
        override fun deserialize(bytes: ByteArray): DecodeResult<FixtureSession> =
            DecodeResult.Ok(ReplayFixtures.initial(ReplayFixtures.SHORT_MOVES))
    }

    private val fixtureScore = object : ScoreModel<FixtureSession> {
        override fun completion(session: FixtureSession): CompletionRecord? = null
    }

    private class ThrowingCompletionRepository(private val failure: Throwable) : CompletionRepository by FakeCompletionRepository() {
        var reads = 0
        override suspend fun recordReplay(puzzleRef: PuzzleRef): SolveReplay? {
            reads++
            throw failure
        }
        override suspend fun latestReplay(puzzleRef: PuzzleRef): SolveReplay? {
            reads++
            throw failure
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
