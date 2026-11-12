package com.ivandsky.puzzlebox.history

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.domain.ComputeStreak
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.domain.LoadCustomReplay
import com.ivandsky.puzzlebox.domain.LoadDailyReplay
import com.ivandsky.puzzlebox.domain.LoadLegReplay
import com.ivandsky.puzzlebox.domain.LoadSharedPuzzle
import com.ivandsky.puzzlebox.domain.LoadSolveReplay
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameSessionCodec
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.game.ScoreModel
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.replay.FixtureSession
import com.ivandsky.puzzlebox.testing.replay.ReplayFixtures
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchResultOutbox
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Game-free history fixtures (ADR-0001: :feature:history:impl never sees a games:* module):
 * stand-in [TestGames] plugins registered under the real ids, so rows carry the game
 * names exactly as production resolves them through the registry.
 */
object HistoryTestSupport {

    val ZIP = GameId("zip")
    val TANGO = GameId("tango")
    val QUEENS = GameId("queens")

    const val PUZZLE_NUMBER = 592
    const val TANGO_PUZZLE_NUMBER = 301
    const val QUEENS_PUZZLE_NUMBER = 916

    val registry: GameRegistry = TestGames.registry(listOf(ZIP, TANGO, QUEENS))

    /** [registry] with [hintsRanked] ranking fewest hints first (Strands' order). */
    fun registry(hintsRanked: Set<GameId>): GameRegistry = GameRegistry(
        listOf(ZIP, TANGO, QUEENS).map { if (it in hintsRanked) hintsFirst(TestGames.plugin(it)) else TestGames.plugin(it) },
    )

    private fun hintsFirst(base: GamePlugin<Unit, TestGames.Session, TestGames.Model>) = GamePlugin(
        definition = base.definition,
        rules = base.rules,
        session = base.session,
        score = object : ScoreModel<TestGames.Session> {
            override fun completion(session: TestGames.Session): CompletionRecord? = null
            override val rankOrder: RankOrder = RankOrder.HintsThenTime
        },
        stateOf = base.stateOf,
        initialSession = base.initialSession,
        refOf = base.refOf,
        playAgainSession = base.playAgainSession,
        rendererModel = base.rendererModel,
    )

    /** Fixed clock: 12:00 UTC Nov 3 2026 is the morning of Nov 3 in LA, so the puzzle day is [TODAY]. */
    val TODAY: LocalDate = LocalDate.of(2026, 11, 3)
    private val clock: Clock = Clock.fixed(Instant.parse("2026-11-03T12:00:00Z"), ZoneOffset.UTC)

    /** History's ViewModel over [daily]/[completions] and the production use cases. */
    fun viewModel(
        daily: DailyRepository,
        completions: CompletionRepository,
        hintsRanked: Set<GameId> = emptySet(),
        opening: HistoryKey = HistoryKey(),
        flags: ReleaseFlags = FakeReleaseFlags(),
        games: GameRegistry = registry(hintsRanked),
    ): HistoryViewModel {
        val observeGameHistory = ObserveGameHistory(games, daily, completions)
        val puzzleDay = GetPuzzleDay(games, clock)
        return HistoryViewModel(
            observeGameHistory = observeGameHistory,
            observeGameStats = ObserveGameStats(
                registry = games,
                observeGameHistory = observeGameHistory,
                computeStreak = ComputeStreak(puzzleDay = puzzleDay),
            ),
            registry = games,
            // No play UIs: every game reads GameBrand.Neutral.
            playRegistry = GamePlayRegistry(emptyList()),
            puzzleDay = puzzleDay,
            loadReplay = loadReplay(flags, games, daily, completions),
            opening = opening,
        )
    }

    /** The production replay entry over the given stores; the stores nothing here reads stay empty fakes. */
    fun loadReplay(
        flags: ReleaseFlags,
        games: GameRegistry,
        daily: DailyRepository,
        completions: CompletionRepository,
    ): LoadSolveReplay {
        val received = FakeReceivedPuzzleRepository()
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
            completions = completions,
            dispatchers = InlineDispatcherProvider,
        )
    }

    /** The fixture game: the only plugin whose replay format [ReplayFixtures] writes. */
    val replayRegistry: GameRegistry = GameRegistry(listOf(fixturePlugin()))

    private fun fixturePlugin(): GamePlugin<Unit, FixtureSession, TestGames.Model> {
        val base = TestGames.plugin(ReplayFixtures.GAME)
        val initial = ReplayFixtures.initial(ReplayFixtures.SHORT_MOVES)
        return GamePlugin(
            definition = base.definition,
            rules = base.rules,
            session = object : GameSessionCodec<FixtureSession> {
                override fun serialize(session: FixtureSession): ByteArray = ByteArray(0)
                override fun deserialize(bytes: ByteArray): DecodeResult<FixtureSession> = DecodeResult.Ok(initial)
            },
            score = object : ScoreModel<FixtureSession> {
                override fun completion(session: FixtureSession): CompletionRecord? = null
            },
            stateOf = { },
            initialSession = { _: Puzzle -> DecodeResult.Ok(initial) },
            refOf = base.refOf,
            playAgainSession = base.playAgainSession,
            rendererModel = { _, _, _ -> TestGames.Model },
            replay = ReplayFixtures.replay,
        )
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

    fun entry(day: LocalDate, puzzleNumber: Int? = PUZZLE_NUMBER, fetchedAtMs: Long = 0L) = DailyEntry(
        gameId = ZIP,
        day = day,
        puzzleNumber = puzzleNumber,
        variant = "zip/n5",
        fetchedAtMs = fetchedAtMs,
    )

    fun record(day: LocalDate, timeMs: Long = 95_000, hints: Int = 2) = CompletionRecord(
        puzzleRef = TestGames.Ref(ZIP, PuzzleKey.Daily(day, PUZZLE_NUMBER)),
        time = timeMs,
        hints = hints,
        stats = RecordStats(schemaVersion = 1, blob = """{"v":1,"mistakes":0}"""),
    )

    fun tangoEntry(day: LocalDate, puzzleNumber: Int? = TANGO_PUZZLE_NUMBER, fetchedAtMs: Long = 0L) = DailyEntry(
        gameId = TANGO,
        day = day,
        puzzleNumber = puzzleNumber,
        variant = "tango/n6",
        fetchedAtMs = fetchedAtMs,
    )

    fun tangoRecord(day: LocalDate, timeMs: Long = 61_000, hints: Int = 0) = CompletionRecord(
        puzzleRef = TestGames.Ref(TANGO, PuzzleKey.Daily(day, TANGO_PUZZLE_NUMBER)),
        time = timeMs,
        hints = hints,
        stats = RecordStats(schemaVersion = 1, blob = """{"v":1,"mistakes":0}"""),
    )

    fun queensRecord(day: LocalDate) = CompletionRecord(
        puzzleRef = TestGames.Ref(QUEENS, PuzzleKey.Daily(day, QUEENS_PUZZLE_NUMBER)),
        time = 41_000L,
        hints = 1,
        stats = RecordStats(schemaVersion = 1, blob = "{}"),
    )
}

/** Epoch-ms for a day's start (UTC) — stable "captured" timestamps in tests. */
fun fetchedAtMs(day: LocalDate): Long = day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
