package com.ivandsky.puzzlebox.home

import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.domain.ObserveLatestSolvedToday
import com.ivandsky.puzzlebox.domain.ObserveLeaderboard
import com.ivandsky.puzzlebox.domain.ObserveResultRank
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.repository.DailyEntry
import com.ivandsky.puzzlebox.data.repository.DailyToday
import com.ivandsky.puzzlebox.data.update.AppRelease
import com.ivandsky.puzzlebox.data.update.AppReleaseRepository
import com.ivandsky.puzzlebox.domain.AppBuild
import com.ivandsky.puzzlebox.domain.ComputeStreak
import com.ivandsky.puzzlebox.domain.GameStats
import com.ivandsky.puzzlebox.domain.GetAppUpdate
import com.ivandsky.puzzlebox.domain.GetNextRollover
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.domain.SyncTodayPuzzles
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.FetchError
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.game.ScoreModel
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.WeekDay
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeAppReleaseRepository
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Home's state. The Today tiles (A10) are today's set over every registered game — Zip
 * and Tango here, in registry order, every brand neutral (no game UI registered). The
 * teaser observes the GAME-AGNOSTIC merged history (fix round 5, defect C); Queens is not
 * registered, so its rows exercise the registry-name fallback. The CTA targets the saved
 * game, else the next unsolved one (A13, SC11); the streak card's week is the last 7
 * puzzle days ending today (A8), over the core:testing fakes.
 */
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val zip = GameId("zip")
    private val tango = GameId("tango")
    private val day = LocalDate.of(2026, 11, 4)
    // The test games roll over at UTC midnight: midday UTC is [day].
    private val clock = Clock.fixed(Instant.parse("2026-11-04T12:00:00Z"), ZoneOffset.UTC)
    private val neutral = GameBrand.Neutral
    private val timeFirst = RankOrder.TimeThenHints

    private data class Deps(
        val vm: HomeViewModel,
        val daily: FakeDailyRepository,
        val completions: FakeCompletionRepository,
        val saved: FakeSavedPlayRepository,
        val identity: FakeIdentityService,
    )

    private fun TestScope.viewModel(
        identity: FakeIdentityService = FakeIdentityService(initial = null),
        collect: Boolean = true,
        hintsRanked: Set<GameId> = emptySet(),
        release: AppRelease? = null,
        releases: AppReleaseRepository = FakeAppReleaseRepository(release),
        boards: Map<LeaderboardRef, List<LeaderboardEntry>> = emptyMap(),
        summariesShown: Boolean = false,
        savedState: SavedStateHandle = SavedStateHandle(),
    ): Deps {
        val registry = GameRegistry(
            listOf(zip, tango).map { if (it in hintsRanked) hintsFirst(TestGames.plugin(it)) else TestGames.plugin(it) },
        )
        val daily = FakeDailyRepository()
        val completions = FakeCompletionRepository()
        val saved = FakeSavedPlayRepository()
        val calendar = FakeGameCalendar(day)
        val sync = SyncTodayPuzzles(registry, daily, calendar)
        val history = ObserveGameHistory(registry, daily, completions)
        val puzzleDay = GetPuzzleDay(registry, clock)
        val vm = HomeViewModel(
            observeGameHistory = history,
            observeGameStats = ObserveGameStats(registry, history, ComputeStreak(puzzleDay)),
            todaySet = ObserveTodaySet(registry, saved, completions, calendar, daily, sync),
            identityService = identity,
            registry = registry,
            brands = GamePlayRegistry(emptyList()),
            puzzleDay = puzzleDay,
            nextRollover = GetNextRollover(registry, clock),
            syncTodayPuzzles = sync,
            getAppUpdate = GetAppUpdate(
                releases,
                AppBuild(versionName = "1.2.0", checksForUpdates = true),
            ),
            observeLatestSolvedToday = ObserveLatestSolvedToday(completions, calendar, registry),
            observeResultRank = ObserveResultRank(
                ObserveLeaderboard(identity, FakeLeaderboardRepository(boards), registry, calendar),
                registry,
            ),
            savedState = savedState,
        )
        vm.setSummariesShown(summariesShown)
        if (collect) backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        return Deps(vm, daily, completions, saved, identity)
    }

    /** [plugin] ranking its bests hints first, as Strands does. */
    private fun <P> hintsFirst(
        plugin: GamePlugin<P, TestGames.Session, TestGames.Model>,
    ): GamePlugin<P, TestGames.Session, TestGames.Model> = GamePlugin(
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
        replaySession = plugin.replaySession,
        rendererModel = plugin.rendererModel,
    )

    private fun record(game: GameId, on: LocalDate, timeMs: Long) = CompletionRecord(
        puzzleRef = TestGames.Ref(game, PuzzleKey.Daily(on, 916)),
        time = timeMs,
        hints = 0,
        stats = RecordStats(schemaVersion = 1, blob = "{}"),
    )

    @Test
    fun `first frame lists every game with no status yet, the empty teaser and current identity`() = runTest {
        val ann = Identity("u1", "Ann", null)
        val deps = viewModel(identity = FakeIdentityService(initial = ann), collect = false)

        assertEquals(
            HomeUiState(
                today = listOf(
                    HomeUiState.TodayRow(zip, "Zip", status = null, brand = neutral, rankOrder = timeFirst),
                    HomeUiState.TodayRow(tango, "Tango", status = null, brand = neutral, rankOrder = timeFirst),
                ),
                identity = ann,
                history = emptyList(),
                puzzleDay = day,
                playTarget = zip,
                stats = GameStats(0, 0, 0, 0, null, null, best = null, rankOrder = RankOrder.TimeThenHints),
                week = (6L downTo 0L).map { WeekDay(day.minusDays(it), emptyList(), isToday = it == 0L) },
                rolloverAt = Instant.parse("2026-11-05T00:00:00Z"),
            ),
            deps.vm.uiState.value,
        )
    }

    @Test
    fun `the week is the last 7 puzzle days ending today, each with its solved games - A8`() = runTest {
        val deps = viewModel()
        deps.completions.onCompleted(record(zip, day, timeMs = 36_000L))
        deps.completions.onCompleted(record(tango, day.minusDays(2), timeMs = 50_000L))
        deps.completions.onCompleted(record(zip, day.minusDays(2), timeMs = 40_000L))
        // Outside the window: 7 days back is not in the week.
        deps.completions.onCompleted(record(zip, day.minusDays(7), timeMs = 40_000L))
        // Opened but not solved: no dot.
        deps.daily.addEntry(DailyEntry(tango, day.minusDays(1), 592, "tango/n5", 0L))

        val week = deps.vm.uiState.value.week
        assertEquals((6L downTo 0L).map { day.minusDays(it) }, week.map { it.date })
        assertEquals(listOf(false, false, false, false, false, false, true), week.map { it.isToday })
        assertEquals(listOf(0, 0, 0, 0, 2, 0, 1), week.map { it.solved.size })
    }

    @Test
    fun `stats follow the solved days`() = runTest {
        val deps = viewModel()
        deps.completions.onCompleted(record(zip, day.minusDays(1), timeMs = 40_000L))
        deps.completions.onCompleted(record(zip, day, timeMs = 36_000L))

        val stats = deps.vm.uiState.value.stats
        assertEquals(2, stats.currentStreak)
        assertEquals(2, stats.bestStreak)
        assertEquals(2, stats.solved)
    }

    @Test
    fun `today set shows every game's status in registry order`() = runTest {
        val deps = viewModel()
        deps.saved.save(PlaySlot.Daily(tango, day), byteArrayOf(1), elapsedMs = 42_000L)

        assertEquals(
            listOf(
                HomeUiState.TodayRow(zip, "Zip", TodayStatus.NotStarted, neutral, timeFirst),
                HomeUiState.TodayRow(tango, "Tango", TodayStatus.InProgress(42_000L), neutral, timeFirst),
            ),
            deps.vm.uiState.value.today,
        )
    }

    @Test
    fun `a hints-ranked game's row carries its fewest-hints best - A10`() = runTest {
        val deps = viewModel(hintsRanked = setOf(tango))
        deps.completions.setRecords(
            listOf(
                CompletionRecord(TestGames.Ref(tango, PuzzleKey.Daily(day.minusDays(1), 6)), 10_000L, 2, RecordStats(1, "{}"), isRecord = true),
                CompletionRecord(TestGames.Ref(tango, PuzzleKey.Daily(day.minusDays(2), 5)), 90_000L, 0, RecordStats(1, "{}"), isRecord = true),
                CompletionRecord(TestGames.Ref(zip, PuzzleKey.Daily(day, 916)), 36_000L, 1, RecordStats(1, "{}"), isRecord = true),
            ),
        )

        val rows = deps.vm.uiState.value.today.associateBy { it.game }

        assertEquals(RankOrder.HintsThenTime, rows.getValue(tango).rankOrder)
        assertEquals(BestScore(timeMs = 90_000L, hints = 0), rows.getValue(tango).best)
        assertEquals(timeFirst, rows.getValue(zip).rankOrder)
        assertEquals(BestScore(timeMs = 36_000L, hints = 1), rows.getValue(zip).best)
    }

    @Test
    fun `a solve today flips the game to solved`() = runTest {
        val deps = viewModel()

        deps.completions.onCompleted(record(zip, day, timeMs = 36_000L))

        assertEquals(TodayStatus.Solved(36_000L), deps.vm.uiState.value.today.first { it.game == zip }.status)
        assertFalse(deps.vm.uiState.value.allSolved)
    }

    @Test
    fun `the CTA targets the saved game and continues it - A13 SC11`() = runTest {
        val deps = viewModel()
        assertEquals(zip, deps.vm.uiState.value.playTarget)
        assertFalse(deps.vm.uiState.value.continues)

        // Leaving Tango mid-game writes today's slot: the CTA offers to continue it.
        deps.saved.save(PlaySlot.Daily(tango, day), byteArrayOf(1), elapsedMs = 5_000L)
        assertEquals(tango, deps.vm.uiState.value.playTarget)
        assertTrue(deps.vm.uiState.value.continues)

        // The save goes away (finished or abandoned): back to playing the first game.
        deps.saved.delete(PlaySlot.Daily(tango, day))
        assertEquals(zip, deps.vm.uiState.value.playTarget)
        assertFalse(deps.vm.uiState.value.continues)
    }

    @Test
    fun `yesterday's save or a solved today does not continue`() = runTest {
        val deps = viewModel()

        // Yesterday's unsolved save resumes through History, not Home (A9).
        deps.saved.save(PlaySlot.Daily(zip, day.minusDays(1)), byteArrayOf(1), elapsedMs = 0L)
        assertFalse(deps.vm.uiState.value.continues)

        // A completion for today wins over a lingering save (A13: solved wins).
        deps.saved.save(PlaySlot.Daily(zip, day), byteArrayOf(1), elapsedMs = 0L)
        assertTrue(deps.vm.uiState.value.continues)
        deps.completions.onCompleted(record(zip, day, timeMs = 36_000L))
        assertFalse(deps.vm.uiState.value.continues)
    }

    @Test
    fun `the CTA skips solved games and turns into the countdown when all are done`() = runTest {
        val deps = viewModel()

        deps.completions.onCompleted(record(zip, day, timeMs = 36_000L))
        assertEquals(tango, deps.vm.uiState.value.playTarget)
        assertFalse(deps.vm.uiState.value.allSolved)

        deps.completions.onCompleted(record(tango, day, timeMs = 50_000L))
        assertEquals(zip, deps.vm.uiState.value.playTarget)
        assertTrue(deps.vm.uiState.value.allSolved)
        // The countdown runs to the next roll-over: the test games' UTC midnight.
        assertEquals(Instant.parse("2026-11-05T00:00:00Z"), deps.vm.uiState.value.rolloverAt)
    }

    @Test
    fun `before the set lands the CTA targets the first game`() = runTest {
        val deps = viewModel(collect = false)
        assertEquals(zip, deps.vm.uiState.value.playTarget)
        assertNull(deps.vm.uiState.value.today.first().status)
    }

    @Test
    fun `syncing today fetches every game and marks the one that cannot load offline - A12`() = runTest {
        val deps = viewModel()
        deps.daily.setToday(zip, DailyToday.Unavailable(FetchError.Unavailable()))
        deps.daily.setToday(tango, DailyToday.Fresh(TestGames.puzzle(tango)))

        deps.vm.syncToday()

        assertEquals(1, deps.daily.todayCalls(zip))
        assertEquals(1, deps.daily.todayCalls(tango))
        assertEquals(
            listOf(TodayStatus.Offline, TodayStatus.NotStarted),
            deps.vm.uiState.value.today.map { it.status },
        )
        // An offline game is not solved, so the CTA still opens it (its route retries).
        assertEquals(zip, deps.vm.uiState.value.playTarget)
    }

    @Test
    fun `teaser tracks zip daily repository emissions`() = runTest {
        val deps = viewModel()
        // Nothing captured yet: the honest empty state.
        assertEquals(emptyList<HistoryDay>(), deps.vm.uiState.value.history)

        // A day lands in the compete loop's store (Play fetches/History captures) — the
        // teaser must follow, formatted like the rows the user knows.
        deps.daily.addEntry(DailyEntry(zip, LocalDate.of(2026, 10, 31), 592, "zip/n5", 0L))

        assertEquals(
            listOf(HistoryDay("2026-10-31", "October 31", listOf(HistoryDay.GameResult("Zip", neutral, time = null)))),
            deps.vm.uiState.value.history,
        )
    }

    @Test
    fun `a non-featured game's completion surfaces in the teaser - fix round 5 defect C`() = runTest {
        val deps = viewModel()

        // A solved Tango game writes its record (no Zip capture anywhere) — the teaser
        // must surface it under the registry's display name.
        deps.completions.onCompleted(record(tango, LocalDate.of(2026, 11, 1), 41_000L))

        assertEquals(
            listOf(
                HistoryDay(
                    "2026-11-01",
                    "November 1",
                    listOf(HistoryDay.GameResult("Tango", neutral, formatDuration(41_000L))),
                ),
            ),
            deps.vm.uiState.value.history,
        )
    }

    @Test
    fun `teaser keeps the newest three days before today, one row per day`() = runTest {
        val deps = viewModel()
        for (offset in 1L..5L) {
            deps.daily.addEntry(DailyEntry(zip, day.minusDays(offset), 592, "zip/n5", 0L))
        }
        // The newest past day has two games; tango lands first but lists after zip.
        deps.completions.onCompleted(record(tango, day.minusDays(1), timeMs = 61_000L))
        // Today lives in the Today tiles, never in the teaser.
        deps.completions.onCompleted(record(zip, day, timeMs = 36_000L))

        val history = deps.vm.uiState.value.history
        assertEquals(listOf("2026-11-03", "2026-11-02", "2026-11-01"), history.map { it.isoDay })
        assertEquals(history.size, history.map { it.isoDay }.toSet().size)
        assertEquals(
            listOf(HistoryDay.GameResult("Zip", neutral, null), HistoryDay.GameResult("Tango", neutral, formatDuration(61_000L))),
            history.first().games,
        )
    }

    @Test
    fun `identity changes reach the state`() = runTest {
        val deps = viewModel()
        val ann = Identity("u1", "Ann", null)

        deps.identity.currentIdentity = ann

        assertEquals(ann, deps.vm.uiState.value.identity)
    }

    @Test
    fun `a newer release with checks on sets the update url - A4 A7`() = runTest {
        val url = "https://github.com/ivanDsky/PuzzleBox/releases/tag/v1.3.0"
        val deps = viewModel(release = AppRelease(tag = "v1.3.0", url = url))

        deps.vm.checkForUpdate()

        assertEquals(url, deps.vm.uiState.value.updateUrl)
    }

    @Test
    fun `no release leaves the update url null`() = runTest {
        val deps = viewModel(release = null)

        deps.vm.checkForUpdate()

        assertNull(deps.vm.uiState.value.updateUrl)
    }

    @Test
    fun `a resume that finds no release clears a known update url - Ruling 05`() = runTest {
        val url = "https://github.com/ivanDsky/PuzzleBox/releases/tag/v1.3.0"
        val releases = FakeAppReleaseRepository(AppRelease(tag = "v1.3.0", url = url))
        val deps = viewModel(releases = releases)

        deps.vm.checkForUpdate()
        assertEquals(url, deps.vm.uiState.value.updateUrl)

        releases.release = null
        deps.vm.checkForUpdate()
        assertNull(deps.vm.uiState.value.updateUrl)
    }

    @Test
    fun `a picked day stays selected while history lists it and clears once history drops it`() = runTest {
        val deps = viewModel()
        deps.completions.setRecords(listOf(record(zip, day.minusDays(1), 40_000L), record(zip, day.minusDays(2), 40_000L)))

        deps.vm.selectDay("2026-11-02")
        assertEquals("2026-11-02", deps.vm.uiState.value.selectedDay)

        deps.completions.setRecords(listOf(record(zip, day.minusDays(1), 40_000L)))
        assertEquals(listOf("2026-11-03"), deps.vm.uiState.value.history.map { it.isoDay })
        assertNull(deps.vm.uiState.value.selectedDay)

        // Gone for good: the day listed again does not bring the pick back.
        deps.completions.setRecords(listOf(record(zip, day.minusDays(1), 40_000L), record(zip, day.minusDays(2), 40_000L)))
        assertNull(deps.vm.uiState.value.selectedDay)
    }

    @Test
    fun `a pick restored after process death starts selected once history lists it`() = runTest {
        val handle = SavedStateHandle(mapOf(SELECTED_DAY_KEY to "2026-11-02"))
        val deps = viewModel(savedState = handle, collect = false)
        deps.completions.setRecords(listOf(record(zip, day.minusDays(2), 40_000L)))

        assertEquals("2026-11-02", deps.vm.uiState.first { it.history.isNotEmpty() }.selectedDay)
    }

    @Test
    fun `a check that never completes keeps the url null and today and history flowing - A6 S5`() = runTest {
        val deps = viewModel(collect = false, releases = NeverAnsweringAppReleaseRepository)
        deps.vm.checkForUpdate()

        // The first frame is the seed: every game listed, status unknown, no pill.
        val first = deps.vm.uiState.value
        assertEquals(listOf(zip, tango), first.today.map { it.game })
        assertTrue(first.today.all { it.status == null })
        assertNull(first.updateUrl)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { deps.vm.uiState.collect {} }
        deps.daily.addEntry(DailyEntry(zip, LocalDate.of(2026, 10, 31), 592, "zip/n5", 0L))

        val state = deps.vm.uiState.value
        assertEquals(listOf(TodayStatus.NotStarted, TodayStatus.NotStarted), state.today.map { it.status })
        assertEquals(listOf("2026-10-31"), state.history.map { it.isoDay })
        assertNull(state.updateUrl)
    }

    private fun entry(uid: String, seconds: Int) =
        LeaderboardEntry(uid, uid, "x/2026-11-04/1", seconds * 1_000L, 0, 1_700_000_000_000)

    /** [game]'s board with [me] twelfth: eleven faster players ahead. */
    private fun twelfth(game: GameId) = LeaderboardRef(game.value, day) to
        (1..11).map { entry("p$it", it) } + entry("me", 60)

    private suspend fun Deps.solve(game: GameId, at: String) = completions.onCompleted(
        CompletionRecord(
            puzzleRef = TestGames.Ref(game, PuzzleKey.Daily(day, 100)),
            time = 30_000L,
            hints = 0,
            stats = RecordStats(schemaVersion = 1, blob = "{}"),
            firstCompletedAt = Instant.parse(at),
            isRecord = true,
        ),
    )

    private val me = Identity("me", "Me", null)

    @Test
    fun `a signed-in player ranked on the latest solved game gets that rank`() = runTest {
        val deps = viewModel(identity = FakeIdentityService(initial = me), boards = mapOf(twelfth(zip)), summariesShown = true)
        deps.solve(zip, "2026-11-04T08:00:00Z")

        assertEquals(TodayRank(zip, "Zip", 12), deps.vm.uiState.value.rank)
    }

    @Test
    fun `a signed-out player has no rank`() = runTest {
        val deps = viewModel(boards = mapOf(twelfth(zip)), summariesShown = true)
        deps.solve(zip, "2026-11-04T08:00:00Z")

        assertNull(deps.vm.uiState.value.rank)
    }

    @Test
    fun `a hidden rank gives none`() = runTest {
        val deps = viewModel(identity = FakeIdentityService(initial = me), summariesShown = true)
        deps.solve(zip, "2026-11-04T08:00:00Z")

        assertNull(deps.vm.uiState.value.rank)
    }

    @Test
    fun `no solve today gives no rank`() = runTest {
        val deps = viewModel(identity = FakeIdentityService(initial = me), boards = mapOf(twelfth(zip)), summariesShown = true)

        assertNull(deps.vm.uiState.value.rank)
    }

    @Test
    fun `a new latest solve of another game moves the rank to it`() = runTest {
        val deps = viewModel(
            identity = FakeIdentityService(initial = me),
            boards = mapOf(twelfth(zip), twelfth(tango)),
            summariesShown = true,
        )
        deps.solve(zip, "2026-11-04T08:00:00Z")
        assertEquals(TodayRank(zip, "Zip", 12), deps.vm.uiState.value.rank)

        deps.solve(tango, "2026-11-04T09:00:00Z")

        assertEquals(TodayRank(tango, "Tango", 12), deps.vm.uiState.value.rank)
    }

    @Test
    fun `a ranked player has no rank while Today shows no summaries, and gets it once it does`() = runTest {
        val deps = viewModel(identity = FakeIdentityService(initial = me), boards = mapOf(twelfth(zip)))
        deps.solve(zip, "2026-11-04T08:00:00Z")
        assertNull(deps.vm.uiState.value.rank)

        deps.vm.setSummariesShown(true)
        assertEquals(TodayRank(zip, "Zip", 12), deps.vm.uiState.value.rank)

        deps.vm.setSummariesShown(false)
        assertNull(deps.vm.uiState.value.rank)
    }

    /** A release check stuck on the network: it never answers. */
    private object NeverAnsweringAppReleaseRepository : AppReleaseRepository {
        override suspend fun latest(): AppRelease? = awaitCancellation()
    }
}

/** The CTA continues a saved play: its target is in progress. */
private val HomeUiState.continues: Boolean
    get() = target?.status is TodayStatus.InProgress
