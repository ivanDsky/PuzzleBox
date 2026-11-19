package com.ivandsky.puzzlebox.app

import androidx.lifecycle.SavedStateHandle
import com.ivandsky.puzzlebox.app.leaderboard.DailyReplayPublisher
import com.ivandsky.puzzlebox.app.screens.ProfileViewModel
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.common.di.commonModule
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.compete.domain.ObserveMyDuels
import com.ivandsky.puzzlebox.create.CreateViewModel
import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.create.editor.EditorViewModel
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.cloud.CloudSync
import com.ivandsky.puzzlebox.data.cloud.CloudSyncRunner
import com.ivandsky.puzzlebox.data.cloud.OversizedDrafts
import com.ivandsky.puzzlebox.data.cloud.OwnerScope
import com.ivandsky.puzzlebox.data.cloud.SavedBoardsCloud
import com.ivandsky.puzzlebox.data.cloud.SignedInOwnerScope
import com.ivandsky.puzzlebox.data.cloud.SyncStateStore
import com.ivandsky.puzzlebox.data.cloud.SyncedKind
import com.ivandsky.puzzlebox.data.di.MatchRemote
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRepository
import com.ivandsky.puzzlebox.data.linkedin.LinkedInAccount
import com.ivandsky.puzzlebox.data.linkedin.LinkedInDays
import com.ivandsky.puzzlebox.data.linkedin.LinkedInPosts
import com.ivandsky.puzzlebox.data.match.MatchRepository
import com.ivandsky.puzzlebox.data.match.MatchResultPoster
import com.ivandsky.puzzlebox.data.match.MatchResultSync
import com.ivandsky.puzzlebox.data.match.RoutingMatchRepository
import com.ivandsky.puzzlebox.data.repository.CustomBoardRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.data.repository.SharedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.data.visibility.HiddenPuzzles
import com.ivandsky.puzzlebox.database.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.domain.AppBuild
import com.ivandsky.puzzlebox.domain.ComputeStreak
import com.ivandsky.puzzlebox.domain.GetFeaturedGame
import com.ivandsky.puzzlebox.domain.GetNextRollover
import com.ivandsky.puzzlebox.domain.LoadSolveReplay
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.domain.ObserveLeaderboard
import com.ivandsky.puzzlebox.domain.ObserveResultRank
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.SyncTodayPuzzles
import com.ivandsky.puzzlebox.domain.linkedin.ObserveLinkedInDay
import com.ivandsky.puzzlebox.domain.linkedin.ObserveLinkedInDayView
import com.ivandsky.puzzlebox.domain.linkedin.SyncLinkedInDay
import com.ivandsky.puzzlebox.domain.ranks.GetPlayerActions
import com.ivandsky.puzzlebox.domain.ranks.ObserveFriendDay
import com.ivandsky.puzzlebox.domain.ranks.ObserveOverall
import com.ivandsky.puzzlebox.domain.ranks.ObserveRankWindow
import com.ivandsky.puzzlebox.game.DailySource
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSessionCookies
import com.ivandsky.puzzlebox.games.queensui.editor.QueensEditorUi
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.gameui.editor.GameEditorUi
import com.ivandsky.puzzlebox.home.HomeViewModel
import com.ivandsky.puzzlebox.home.domain.GetAppUpdate
import com.ivandsky.puzzlebox.home.domain.ObserveLatestSolvedToday
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.GameViewModel
import com.ivandsky.puzzlebox.play.LibraryViewModel
import com.ivandsky.puzzlebox.play.PlaySessionStore
import com.ivandsky.puzzlebox.play.ResultViewModel
import com.ivandsky.puzzlebox.play.api.GameKey
import com.ivandsky.puzzlebox.play.api.ResultKey
import com.ivandsky.puzzlebox.play.domain.FinishLeg
import com.ivandsky.puzzlebox.play.domain.ListCommunityPuzzles
import com.ivandsky.puzzlebox.play.domain.LoadPastDay
import com.ivandsky.puzzlebox.play.domain.ObserveArchiveMonth
import com.ivandsky.puzzlebox.play.domain.ObserveArchiveProgress
import com.ivandsky.puzzlebox.play.domain.ObserveLegTarget
import com.ivandsky.puzzlebox.play.domain.ObservePlayStats
import com.ivandsky.puzzlebox.play.domain.ObserveResultInsights
import com.ivandsky.puzzlebox.play.domain.ReportPuzzle
import com.ivandsky.puzzlebox.play.domain.linkedin.ObserveLinkedInPost
import com.ivandsky.puzzlebox.play.domain.linkedin.SubmitLinkedInResult
import com.ivandsky.puzzlebox.play.domain.nextthing.ObserveResultNextThing
import com.ivandsky.puzzlebox.play.domain.random.LoadRandomDay
import com.ivandsky.puzzlebox.play.domain.random.LoadRandomPuzzle
import com.ivandsky.puzzlebox.play.domain.random.LoadRandomShared
import com.ivandsky.puzzlebox.profile.domain.linkedin.CreateLinkedInLink
import com.ivandsky.puzzlebox.profile.domain.linkedin.ObserveLinkedInAccount
import com.ivandsky.puzzlebox.profile.domain.linkedin.UpdateLinkedInPosting
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInSessionCookies
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.Koin
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module
import org.koin.core.parameter.parametersOf
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
            // LinkedInDays reads through the owner scope, which collects identity; the JVM has no FirebaseApp.
            modules(
                PuzzleBoxApplication.appModules +
                    module { single<IdentityService> { FakeIdentityService() } },
            )
        }
        try {
            assertNotNull(koinApp.koin.get<LinkedInDays>())
        } finally {
            stopKoin()
        }
    }

    /** Profile's LinkedIn row resolves off the real graph: the summary use case and the view model. */
    @Test
    fun `the LinkedIn account summary and the profile view model resolve`() {
        val koinApp = startKoin {
            androidContext(RuntimeEnvironment.getApplication())
            // The owner scope collects identity; the real service would reach FirebaseAuth, and the JVM has no FirebaseApp.
            modules(
                PuzzleBoxApplication.appModules +
                    module { single<IdentityService> { FakeIdentityService() } },
            )
        }
        try {
            PuzzleBoxApplication.prepareCloudSync(koinApp.koin)
            assertNotNull(koinApp.koin.get<ObserveLinkedInAccount>())
            assertNotNull(koinApp.koin.get<UpdateLinkedInPosting>())
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

    /**
     * Each game that posts to LinkedIn reports its LinkedIn route through the registered source. A source
     * bound behind a wrapper must pass it on, or the result page offers that game no Post to LinkedIn row.
     */
    @Test
    fun `every game that posts to LinkedIn reports its route off the real graph`() {
        val koinApp = startKoin {
            androidContext(RuntimeEnvironment.getApplication())
            modules(PuzzleBoxApplication.appModules)
        }
        try {
            val daily = koinApp.koin.get<DailyRepository>()
            val posting = koinApp.koin.get<GameRegistry>().let { it.all + it.unlisted }
                .filter { it.linkedInPlay != null }
                .map { it.definition.id }
            assertEquals(
                mapOf(
                    "queens" to "games/queens/",
                    "tango" to "games/tango/",
                    "zip" to "games/zip/",
                    "minisudoku" to "games/mini-sudoku/",
                ),
                posting.associate { it.value to daily.linkedInGamePath(it) },
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

    /** The result page's Post to LinkedIn: its door, both use cases and the view model that takes them resolve with the LinkedIn parameter on. */
    @Test
    fun `the LinkedIn post door and the result view model resolve off the real graph`() {
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
            val koin = koinApp.koin
            assertNotNull(koin.get<LinkedInPosts>())
            assertNotNull(koin.get<ObserveLinkedInPost>())
            assertNotNull(koin.get<SubmitLinkedInResult>())
            assertNotNull(koin.get<ResultViewModel> { parametersOf(ResultKey("zip", "2026-11-04", 12_340L, 0), true) })
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
            // The community use cases and the hide set they share (inert until the Community screens call them).
            assertNotNull(koin.get<HiddenPuzzles>())
            assertNotNull(koin.get<ListCommunityPuzzles>())
            assertNotNull(koin.get<ReportPuzzle>())
            assertNotNull(koin.get<CustomBoardRepository>())
            assertNotNull(koin.get<GoogleIdTokenProvider>())
            // Regression: ProfileRoute's koinViewModel() needs this definition —
            // without it the first Profile composition throws NoDefinitionFound.
            PuzzleBoxApplication.prepareCloudSync(koin)
            assertNotNull(koin.get<ProfileViewModel> { parametersOf(SavedStateHandle()) })
            // Home route's koinViewModel() and their domain use cases.
            assertNotNull(koin.get<HomeViewModel> { parametersOf(SavedStateHandle()) })
            assertNotNull(koin.get<ObserveGameHistory>())
            assertNotNull(koin.get<ComputeStreak>())
            assertNotNull(koin.get<ObserveGameStats>())
            assertNotNull(koin.get<GetNextRollover>())
            // One sync app-wide: Home and the hub read the same failed fetches.
            assertSame(koin.get<SyncTodayPuzzles>(), koin.get<SyncTodayPuzzles>())
            assertNotNull(koin.get<LoadPastDay>())
            // The library's random pick: a past daily, a community puzzle, and the pick over both.
            assertNotNull(koin.get<LoadRandomDay>())
            assertNotNull(koin.get<LoadRandomShared>())
            assertNotNull(koin.get<LoadRandomPuzzle>())
            assertNotNull(koin.get<LoadSolveReplay>())
            // The game page's archive calendar and its "n of N solved" header.
            assertNotNull(koin.get<ObserveArchiveMonth>())
            assertNotNull(koin.get<ObserveArchiveProgress>())
            // A duel leg's result into the outbox, and the match leg screen's target.
            assertNotNull(koin.get<FinishLeg>())
            assertNotNull(koin.get<ObserveLegTarget>())
            // The Duels tab's rows and its badge count.
            assertNotNull(koin.get<ObserveMyDuels>())
            // The tablet play side panel's stats and the board gate beneath them.
            assertNotNull(koin.get<ObserveLeaderboard>())
            assertNotNull(koin.get<ObserveResultInsights>())
            assertNotNull(koin.get<ObserveResultRank>())
            assertNotNull(koin.get<ObservePlayStats>())
            // Home's resume update check: the use case over the shell-bound installed build;
            // this debug-variant graph never checks.
            assertNotNull(koin.get<GetAppUpdate>())
            // Home's "Play today" pick: the latest official daily solved on each game's own today.
            assertNotNull(koin.get<ObserveLatestSolvedToday>())
            assertEquals(false, koin.get<AppBuild>().checksForUpdates)
            // Today's featured game (Profile's board, the leaderboard) is the registry's first.
            assertEquals(GameId("zip"), koin.get<GetFeaturedGame>()().id)
            // Duel results: onCreate starts the poster on every launch, and the duel screen pokes the same instance.
            assertSame(koin.get<MatchResultPoster>(), koin.get<MatchResultSync>())
            // Duels: the app's repository is the router, so a self code never reaches the backend bound behind it.
            assertTrue(koin.get<MatchRepository>() is RoutingMatchRepository)
            assertNotNull(koin.get<MatchRepository>(MatchRemote))
            // Daily replays: onCreate starts the publisher with the hub flag on, so its whole chain must bind.
            assertNotNull(koin.get<DailyReplayPublisher>())

            // Game aggregation (ADR-0001): every game module's plugin lands in the
            // registry in picker order, and every game's play UI is registered — a
            // missing entry is a wiring bug, loudly here.
            val registry = koin.get<GameRegistry>()
            val games = (registry.all + registry.unlisted).map { it.definition.id.value }
            assertEquals(listOf("zip", "tango", "queens", "minisudoku", "strands", "sudoku"), games)
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
     * binding behind RanksViewModel's positional `get()`s would otherwise surface only when the hub opens.
     */
    @Test
    fun `the Ranks hub resolves off the real graph`() {
        val koinApp = startKoin {
            androidContext(RuntimeEnvironment.getApplication())
            modules(
                PuzzleBoxApplication.appModules + module {
                    single<IdentityService> { FakeIdentityService() }
                    single<ReleaseFlags> { FakeReleaseFlags() }
                },
            )
        }
        try {
            val koin = koinApp.koin
            assertNotNull(koin.viewModel("history.ranks.RanksViewModel", SavedStateHandle()))
            assertNotNull(koin.viewModel("history.ranks.PlayerSheetViewModel"))
            assertNotNull(koin.viewModel("history.ranks.ReplayOptInViewModel"))
            assertNotNull(koin.viewModel("play.GamePageRankViewModel", GameId("zip")))
            assertNotNull(koin.get<ObserveRankWindow>())
            assertNotNull(koin.get<ObserveOverall>())
            assertNotNull(koin.get<GetPlayerActions>())
            assertNotNull(koin.get<ObserveResultRank>())
            assertNotNull(koin.get<ObserveResultNextThing>())
        } finally {
            stopKoin()
        }
    }

    /** Result's next-thing chain is built with the community list (A2: read in DI). */
    @Test
    fun `Result's next thing resolves`() {
        val koinApp = startKoin {
            androidContext(RuntimeEnvironment.getApplication())
            modules(
                PuzzleBoxApplication.appModules + module {
                    single<IdentityService> { FakeIdentityService() }
                    single<ReleaseFlags> { FakeReleaseFlags() }
                },
            )
        }
        try {
            assertNotNull(koinApp.koin.get<ObserveResultNextThing>())
        } finally {
            stopKoin()
        }
    }

    /** The app's Friends/Global reader for the ranks outside the hub is always built with the connections (AppModule). */
    @Test
    fun `the Friends reader is built with the connections`() {
        var connections = 0
        val koinApp = startRanks {
            factory { connections++; ObserveLinkedInDay(days = get(), registry = get()) }
        }
        try {
            assertNotNull(koinApp.koin.get<ObserveFriendDay.Binding>().reader)
            assertEquals(1, connections)
        } finally {
            stopKoin()
        }
    }

    private fun startRanks(overrides: Module.() -> Unit = {}) = startKoin {
        androidContext(RuntimeEnvironment.getApplication())
        modules(
            PuzzleBoxApplication.appModules + module {
                single<IdentityService> { FakeIdentityService() }
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

    /** The five app-database kinds and the boards kind are bound, and saved boards can be deleted everywhere. */
    @Test
    fun `the boards kind and saved-board deletion are bound`() {
        val koinApp = startCloud()
        try {
            val koin = koinApp.koin
            PuzzleBoxApplication.prepareCloudSync(koin)
            assertNotNull(koin.get<OversizedDrafts>())
            assertTrue(koin.get<OwnerScope>() is SignedInOwnerScope)
            assertEquals(
                listOf("boards", "completions", "drafts", "published", "received", "replays", "reveals"),
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

    private fun startCloud(identity: FakeIdentityService = FakeIdentityService()) = startKoin {
        androidContext(RuntimeEnvironment.getApplication())
        modules(
            PuzzleBoxApplication.appModules + module {
                single<IdentityService> { identity }
                single<ReleaseFlags> { FakeReleaseFlags() }
            },
        )
    }
}
