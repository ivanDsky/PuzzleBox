package com.ivandsky.puzzlebox.app

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.app.screens.ProfileViewModel
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.qualifier
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.verify.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
// application = plain Application (showkase-v2-05): with unit-test resources enabled
// (:app runs the browser screenshot suite) Robolectric would boot the REAL
// PuzzleBoxApplication from the merged manifest, whose onCreate startKoin races this
// class's own startKoin on the JVM-global context. The graph under test is
// PuzzleBoxApplication.appModules, built below against any Context.
@Config(sdk = [34], application = android.app.Application::class)
class DiGraphTest : KoinTest {

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
}
