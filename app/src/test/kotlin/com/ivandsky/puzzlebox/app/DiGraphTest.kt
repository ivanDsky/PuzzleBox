package com.ivandsky.puzzlebox.app

import androidx.lifecycle.SavedStateHandle
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.app.screens.ProfileViewModel
import com.ivandsky.puzzlebox.app.leaderboard.DailyReplayPublisher
import com.ivandsky.puzzlebox.data.match.MatchResultPoster
import com.ivandsky.puzzlebox.common.di.commonModule
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.create.CreateViewModel
import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.create.editor.EditorViewModel
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.match.MatchResultSync
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRepository
import com.ivandsky.puzzlebox.data.linkedin.LinkedInDays
import com.ivandsky.puzzlebox.data.repository.CustomBoardRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.data.repository.SharedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.database.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.domain.AppBuild
import com.ivandsky.puzzlebox.domain.ComputeStreak
import com.ivandsky.puzzlebox.domain.GetAppUpdate
import com.ivandsky.puzzlebox.domain.GetFeaturedGame
import com.ivandsky.puzzlebox.domain.GetNextRollover
import com.ivandsky.puzzlebox.domain.LoadPastDay
import com.ivandsky.puzzlebox.domain.LoadSolveReplay
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.domain.ObserveLeaderboard
import com.ivandsky.puzzlebox.domain.ObservePlayStats
import com.ivandsky.puzzlebox.domain.ObserveResultInsights
import com.ivandsky.puzzlebox.domain.ObserveResultRank
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.SyncTodayPuzzles
import com.ivandsky.puzzlebox.domain.linkedin.CreateLinkedInLink
import com.ivandsky.puzzlebox.domain.linkedin.ObserveLinkedInAccount
import com.ivandsky.puzzlebox.domain.linkedin.ObserveLinkedInDay
import com.ivandsky.puzzlebox.domain.linkedin.ObserveLinkedInDayView
import com.ivandsky.puzzlebox.domain.linkedin.SyncLinkedInDay
import com.ivandsky.puzzlebox.domain.ranks.GetPlayerActions
import com.ivandsky.puzzlebox.domain.ranks.ObserveFriendDay
import com.ivandsky.puzzlebox.domain.ranks.ObserveFriendUids
import com.ivandsky.puzzlebox.domain.ranks.ObserveOverall
import com.ivandsky.puzzlebox.domain.ranks.ObserveRankWindow
import com.ivandsky.puzzlebox.game.DailySource
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSessionCookies
import com.ivandsky.puzzlebox.games.queensui.editor.QueensEditorUi
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.gameui.editor.GameEditorUi
import com.ivandsky.puzzlebox.history.HistoryViewModel
import com.ivandsky.puzzlebox.history.api.HistoryKey
import com.ivandsky.puzzlebox.home.HomeViewModel
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.GameViewModel
import com.ivandsky.puzzlebox.play.LibraryViewModel
import com.ivandsky.puzzlebox.play.PlaySessionStore
import com.ivandsky.puzzlebox.play.ResultViewModel
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInSessionCookies
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.data.cloud.CloudSync
import com.ivandsky.puzzlebox.data.cloud.CloudSyncRunner
import com.ivandsky.puzzlebox.data.cloud.OversizedDrafts
import com.ivandsky.puzzlebox.data.cloud.OwnerScope
import com.ivandsky.puzzlebox.data.cloud.SavedBoardsCloud
import com.ivandsky.puzzlebox.data.cloud.SignedInOwnerScope
import com.ivandsky.puzzlebox.data.cloud.SyncStateStore
import com.ivandsky.puzzlebox.data.cloud.SyncedKind
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.linkedin.LinkedInAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.parameter.parametersOf
import org.koin.core.Koin
import org.koin.core.module.Module
import org.koin.core.qualifier.qualifier
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.verify.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// application = DrainTestApplication (plain Application + looper drain) (showkase-v2-05): with unit-test resources enabled
// (:app runs the browser screenshot suite) Robolectric would boot the REAL
// PuzzleBoxApplication from the merged manifest, whose onCreate startKoin races this
// class's own startKoin on the JVM-global context. The graph under test is
// PuzzleBoxApplication.appModules, built below against any Context.
@Config(sdk = [34], application = DrainTestApplication::class)
class DiGraphTest : KoinTest {

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `pure module graph is complete`() {
        // commonModule has no Android dependencies, so static verification applies.
        commonModule.verify()
    }

    /** Nothing collects identity, so the real service builds without touching Firebase. */
    @Test
    fun `identity binds the Firebase service lazily`() {
        val koinApp = startKoin {
            androidContext(RuntimeEnvironment.getApplication())
            modules(PuzzleBoxApplication.appModules)
        }
        try {
            assertNotNull(koinApp.koin.get<IdentityService>())
        } finally {
            stopKoin()
        }
    }

    /** Flag-on resolves LinkedInDays off the real graph; no cookies override, so the real binding must exist. */
    @Test
    fun `LinkedInDays resolves off the real graph with no cookies override`() {
        val koinApp = startKoin {
            androidContext(RuntimeEnvironment.getApplication())
            modules(PuzzleBoxApplication.appModules)
        }
        try {
            assertNotNull(koinApp.koin.get<LinkedInDays>())
        } finally {
            stopKoin()
        }
    }

    /** Profile's LinkedIn row resolves off the real graph: the summary use case and the flag-on view model. */
    @Test
    fun `the LinkedIn account summary and the flag-on profile view model resolve`() {
        val koinApp = startKoin {
            androidContext(RuntimeEnvironment.getApplication())
            modules(PuzzleBoxApplication.appModules)
        }
        try {
            assertNotNull(koinApp.koin.get<ObserveLinkedInAccount>())
            assertNotNull(koinApp.koin.get<ProfileViewModel> { parametersOf(true) })
        } finally {
            stopKoin()
        }
    }

    /**
     * Each LinkedIn game reports its LinkedIn type id through the registered source. A source
     * bound behind a wrapper must pass it on, or the account sync skips that game silently.
     */
    @Test
    fun `every LinkedIn game reports its type id off the real graph`() {
        val koinApp = startKoin {
            androidContext(RuntimeEnvironment.getApplication())
            modules(PuzzleBoxApplication.appModules)
        }
        try {
            val daily = koinApp.koin.get<DailyRepository>()
            assertEquals(
                mapOf("queens" to 3, "tango" to 5, "zip" to 6, "minisudoku" to 7),
                listOf("queens", "tango", "zip", "minisudoku").associateWith { daily.linkedInGameTypeId(GameId(it)) },
            )
        } finally {
            stopKoin()
        }
    }

    /** The LinkedIn use cases resolve off the real graph, so the ViewModels that inject them bind. */
    @Test
    fun `the LinkedIn use cases resolve off the real graph`() {
        val koinApp = startKoin {
            androidContext(RuntimeEnvironment.getApplication())
            modules(
                PuzzleBoxApplication.appModules +
                    module {
                        single<IdentityService> { FakeIdentityService() }
                        single<LinkedInSessionCookies> { FakeLinkedInSessionCookies(null) }
                    },
            )
        }
        try {
            assertNotNull(koinApp.koin.get<SyncLinkedInDay>())
            assertNotNull(koinApp.koin.get<ObserveLinkedInDay>())
            assertNotNull(koinApp.koin.get<ObserveLinkedInDayView>())
            assertNotNull(koinApp.koin.get<CreateLinkedInLink>())
        } finally {
            stopKoin() // close() alone leaves the JVM-global context registered for later tests
        }
    }

    /**
     * The full graph resolves for real under Robolectric: Room, Retrofit, and the
     * DataStore-backed preferences all construct without touching the network.
     */
    @Test
    fun `app module graph resolves at runtime`() {
        val koinApp = startKoin {
            androidContext(RuntimeEnvironment.getApplication())
            // EditorViewModel collects identity; the real service would then reach
            // FirebaseAuth, and the JVM has no FirebaseApp.
            modules(
                PuzzleBoxApplication.appModules +
                    module { single<IdentityService> { FakeIdentityService() } },
            )
        }
        try {
            val koin = koinApp.koin
            assertNotNull(koin.get<DispatcherProvider>())
            assertNotNull(koin.get<PuzzleBoxDatabase>())
            // The generic daily stack: one repository over every game's DailySource.
            assertNotNull(koin.get<DailyRepository>())
            assertEquals(
                setOf(GameId("zip"), GameId("tango"), GameId("queens"), GameId("minisudoku"), GameId("sudoku"), GameId("strands")),
                koin.getAll<DailySource>().map { it.gameId }.toSet(),
            )
            // One bound source per game — the repository's associateBy would silently keep the last.
            val boundIds = koin.getAll<DailySource>().map { it.gameId }
            assertEquals("a gameId is bound to more than one DailySource", boundIds.distinct(), boundIds)
            assertNotNull(koin.get<UserPreferencesRepository>())
            // Backend bindings resolve against the app-provided scope.
            // The real binding is pinned by `identity binds the Firebase service lazily`.
            assertNotNull(koin.get<IdentityService>())
            assertNotNull(koin.get<LeaderboardRepository>())
            assertNotNull(koin.get<SharedPuzzleRepository>())
            assertNotNull(koin.get<CustomBoardRepository>())
            assertNotNull(koin.get<GoogleIdTokenProvider>())
            // Regression: ProfileRoute's koinViewModel() needs this definition —
            // without it the first Profile composition throws NoDefinitionFound.
            assertNotNull(koin.get<ProfileViewModel> { parametersOf(SavedStateHandle()) })
            // Home/History routes' koinViewModel() and their domain use cases.
            assertNotNull(koin.get<HomeViewModel> { parametersOf(SavedStateHandle()) })
            assertNotNull(koin.get<HistoryViewModel> { parametersOf(HistoryKey()) })
            assertNotNull(koin.get<ObserveGameHistory>())
            assertNotNull(koin.get<ComputeStreak>())
            assertNotNull(koin.get<ObserveGameStats>())
            assertNotNull(koin.get<GetNextRollover>())
            // One sync app-wide: Home and the hub read the same failed fetches.
            assertSame(koin.get<SyncTodayPuzzles>(), koin.get<SyncTodayPuzzles>())
            assertNotNull(koin.get<LoadPastDay>())
            assertNotNull(koin.get<LoadSolveReplay>())
            // The tablet play side panel's stats and the board gate beneath them.
            assertNotNull(koin.get<ObserveLeaderboard>())
            assertNotNull(koin.get<ObserveResultInsights>())
            assertNotNull(koin.get<ObserveResultRank>())
            assertNotNull(koin.get<ObservePlayStats>())
            // Home's resume update check: the use case over the shell-bound installed build;
            // this debug-variant graph never checks.
            assertNotNull(koin.get<GetAppUpdate>())
            assertEquals(false, koin.get<AppBuild>().checksForUpdates)
            // Today's featured game (Profile's board, the leaderboard) is the registry's first.
            assertEquals(GameId("zip"), koin.get<GetFeaturedGame>()().id)
            // Duel results: onCreate starts the poster on every launch, and the duel screen pokes the same instance.
            assertSame(koin.get<MatchResultPoster>(), koin.get<MatchResultSync>())
            // Daily replays: onCreate starts the publisher with the hub flag on, so its whole chain must bind.
            assertNotNull(koin.get<DailyReplayPublisher>())

            // Game aggregation (ADR-0001): every game module's plugin lands in the
            // registry in picker order, and every game's play UI is registered — a
            // missing entry is a wiring bug, loudly here.
            val registry = koin.get<GameRegistry>()
            val games = (registry.all + registry.unlisted).map { it.definition.id.value }
            assertEquals(listOf("zip", "tango", "queens", "strands", "minisudoku", "sudoku"), games)
            val playUis = koin.get<GamePlayRegistry>()
            for (id in games) assertNotNull("no play UI registered for $id", playUis[GameId(id)])
            // The one shared play session store resolves off the real graph.
            assertNotNull(koin.get<PlaySessionStore>())

            // Navigation bindings: saved plays, the per-game calendar, the
            // progress cues, and the play routes' ViewModels with their key parameters.
            assertNotNull(koin.get<SavedPlayRepository>())
            assertNotNull(koin.get<GameCalendar>())
            assertNotNull(koin.get<ObserveTodaySet>())
            assertNotNull(koin.get<LibraryViewModel>())
            // A past, uncached day: the slot opens as a cache miss, never today's network fetch.
            assertNotNull(koin.get<GameViewModel> { parametersOf(GameKey("zip", "2000-01-01")) }.play)
            assertNotNull(koin.get<ResultViewModel> { parametersOf(ResultKey("zip", "2026-11-04", 12_340L, 0)) })

            // Editor mode (ADR-0019): Queens registers its editor UI, and the Create routes'
            // ViewModels resolve (an unknown draft id loads as Missing, never a crash).
            assertNotNull(koin.get<GameEditorRegistry>()[GameId("queens")])
            assertNotNull(koin.get<CreateViewModel>())
            assertNotNull(koin.get<EditorViewModel> { parametersOf(EditorKey("no-such-draft")) })
        } finally {
            stopKoin() // close() alone leaves the JVM-global context registered for later tests
        }
    }

    /** Two editor UIs for one game is a wiring bug that fails the registry's first resolve. */
    /**
     * The hub's view models are `internal` to their feature modules, so they are asked for by class name; a missing
     * binding behind RanksViewModel's thirteen positional `get()`s would otherwise surface only when the hub opens.
     */
    @Test
    fun `the Ranks hub resolves off the real graph with the hub on`() {
        val koinApp = startKoin {
            androidContext(RuntimeEnvironment.getApplication())
            modules(
                PuzzleBoxApplication.appModules + module {
                    single<IdentityService> { FakeIdentityService() }
                    single<ReleaseFlags> { FakeReleaseFlags(ReleaseFlag.LeaderboardHub) }
                },
            )
        }
        try {
            val koin = koinApp.koin
            assertNotNull(koin.viewModel("history.ranks.RanksViewModel", SavedStateHandle()))
            assertNotNull(koin.viewModel("history.ranks.PlayerSheetViewModel"))
            assertNotNull(koin.viewModel("history.ranks.ReplayOptInViewModel"))
            assertNotNull(koin.viewModel("play.GamePageBoardViewModel", GameId("zip")))
            assertNotNull(koin.get<ObserveRankWindow>())
            assertNotNull(koin.get<ObserveOverall>())
            assertNotNull(koin.get<GetPlayerActions>())
            assertNotNull(koin.get<ObserveResultRank>())
        } finally {
            stopKoin()
        }
    }

    /**
     * The app decides the reader of the Friends/Global setting the ranks outside the hub follow (AppModule). Hub off
     * there is none, whatever LinkedIn's flag says: the use cases that would listen for friends and read the
     * connections cannot be built here, so asking for either fails the test.
     */
    @Test
    fun `with the hub off the ranks outside the hub get no Friends reader`() {
        val koinApp = startRanks(FakeReleaseFlags(ReleaseFlag.LinkedInAccount)) {
            factory<ObserveFriendUids> { error("friends were listened for with the hub off") }
            factory<ObserveLinkedInDay> { error("the connections were read with the hub off") }
        }
        try {
            val koin = koinApp.koin
            assertNull(koin.get<ObserveFriendDay.Binding>().reader)
            assertNotNull(koin.get<ObserveResultRank>())
            assertNotNull(koin.viewModel("play.GamePageBoardViewModel", GameId("zip")))
        } finally {
            stopKoin()
        }
    }

    @Test
    fun `with the hub on and LinkedIn off the Friends reader is built without the connections`() {
        val koinApp = startRanks(FakeReleaseFlags(ReleaseFlag.LeaderboardHub)) {
            factory<ObserveLinkedInDay> { error("the connections were read with LinkedIn off") }
        }
        try {
            assertNotNull(koinApp.koin.get<ObserveFriendDay.Binding>().reader)
        } finally {
            stopKoin()
        }
    }

    @Test
    fun `with the hub and LinkedIn on the Friends reader is built with the connections`() {
        var connections = 0
        val koinApp = startRanks(FakeReleaseFlags(ReleaseFlag.LeaderboardHub, ReleaseFlag.LinkedInAccount)) {
            factory { connections++; ObserveLinkedInDay(days = get(), registry = get()) }
        }
        try {
            assertNotNull(koinApp.koin.get<ObserveFriendDay.Binding>().reader)
            assertEquals(1, connections)
        } finally {
            stopKoin()
        }
    }

    /** The developer menu flips a flag while the app runs: the next screen opened must see it. */
    @Test
    fun `a hub flag flipped while the app runs decides the next Friends reader asked for`() {
        val on = MutableStateFlow(emptySet<ReleaseFlag>())
        val koinApp = startRanks(object : ReleaseFlags { override val enabled = on })
        try {
            val koin = koinApp.koin
            assertNull(koin.get<ObserveFriendDay.Binding>().reader)
            on.value = setOf(ReleaseFlag.LeaderboardHub)
            assertNotNull(koin.get<ObserveFriendDay.Binding>().reader)
            on.value = emptySet()
            assertNull(koin.get<ObserveFriendDay.Binding>().reader)
        } finally {
            stopKoin()
        }
    }

    private fun startRanks(flags: ReleaseFlags, overrides: Module.() -> Unit = {}) = startKoin {
        androidContext(RuntimeEnvironment.getApplication())
        modules(
            PuzzleBoxApplication.appModules + module {
                single<IdentityService> { FakeIdentityService() }
                single<ReleaseFlags> { flags }
                overrides()
            },
        )
    }

    private fun Koin.viewModel(name: String, vararg params: Any) =
        get<Any>(Class.forName("com.ivandsky.puzzlebox.$name").kotlin, null) { parametersOf(*params) }

    @Test
    fun `a second editor UI for one game fails the registry`() {
        val duplicate = module { single<GameEditorUi>(qualifier = qualifier("queens.editor.again")) { QueensEditorUi } }
        val koinApp = startKoin {
            androidContext(RuntimeEnvironment.getApplication())
            modules(PuzzleBoxApplication.appModules + duplicate)
        }
        try {
            val failure = assertThrows(Exception::class.java) { koinApp.koin.get<GameEditorRegistry>() }
            val messages = generateSequence<Throwable>(failure) { it.cause }.mapNotNull { it.message }.toList()
            assertTrue("expected the duplicate-id failure, got $messages", messages.any { "editor UI already registered for queens" in it })
        } finally {
            stopKoin()
        }
    }

    /** Without LinkedInAccount: the five app-database kinds, the engine and the runner; no boards kind, no saved-board deletion. */
    @Test
    fun `the sync graph resolves without the LinkedIn kinds`() {
        val koinApp = startCloud()
        try {
            val koin = koinApp.koin
            PuzzleBoxApplication.prepareCloudSync(koin)
            assertNotNull(koin.get<CloudSync>())
            assertNotNull(koin.get<CloudSyncRunner>())
            assertNotNull(koin.get<OversizedDrafts>())
            assertTrue(koin.get<OwnerScope>() is SignedInOwnerScope)
            assertEquals(
                setOf("completions", "drafts", "published", "received", "replays"),
                koin.getAll<SyncedKind>().map { it.kind }.toSet(),
            )
            assertNull(koin.getOrNull<SavedBoardsCloud>())
        } finally {
            stopKoin()
        }
    }

    /** With LinkedInAccount on: the boards kind joins and saved boards can be deleted everywhere. */
    @Test
    fun `with LinkedInAccount on the boards kind and saved-board deletion are bound`() {
        val koinApp = startCloud(ReleaseFlag.LinkedInAccount)
        try {
            val koin = koinApp.koin
            PuzzleBoxApplication.prepareCloudSync(koin)
            assertEquals(
                listOf("boards", "completions", "drafts", "published", "received", "replays"),
                koin.getAll<SyncedKind>().map { it.kind }.sorted(),
            )
            assertNotNull(koin.get<SavedBoardsCloud>())
            assertNotNull(koin.get<LinkedInDays>())
            assertNotNull(koin.get<LinkedInAccount>())
            // The engine, the erase and the runner all see the same registered kinds.
            assertNotNull(koin.get<CloudSync>())
            assertNotNull(koin.get<CloudSyncRunner>())
        } finally {
            stopKoin()
        }
    }

    /** SignedInOwnerScope starts collecting at construction, so the app creates it before any starter runs. */
    @Test
    fun `the signed-in owner scope is created at start so the last uid is recorded from launch`() {
        val identity = FakeIdentityService(initial = Identity(uid = "u1", displayName = "One", photoUrl = null))
        val koinApp = startCloud(identity = identity)
        try {
            PuzzleBoxApplication.prepareCloudSync(koinApp.koin)
            // Nobody resolved OwnerScope: only the app's start step can have written the uid.
            val last = runBlocking { withTimeout(10_000) { koinApp.koin.get<SyncStateStore>().lastUid.first { it == "u1" } } }
            assertEquals("u1", last)
        } finally {
            stopKoin()
        }
    }

    private fun startCloud(vararg flags: ReleaseFlag, identity: FakeIdentityService = FakeIdentityService()) = startKoin {
        androidContext(RuntimeEnvironment.getApplication())
        modules(
            PuzzleBoxApplication.appModules + module {
                single<IdentityService> { identity }
                single<ReleaseFlags> { FakeReleaseFlags(*flags) }
            },
        )
    }
}
