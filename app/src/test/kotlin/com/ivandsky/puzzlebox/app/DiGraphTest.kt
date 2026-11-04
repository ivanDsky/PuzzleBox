package com.ivandsky.puzzlebox.app

import android.app.Application
import com.ivandsky.puzzlebox.app.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.app.screens.ProfileViewModel
import com.ivandsky.puzzlebox.common.di.commonModule
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.db.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRepository
import com.ivandsky.puzzlebox.data.network.PuzzleFeedApi
import com.ivandsky.puzzlebox.data.repository.DailyPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.QueensDailyRepository
import com.ivandsky.puzzlebox.data.repository.TangoDailyRepository
import com.ivandsky.puzzlebox.data.repository.TangoDailySource
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.PuzzleSource
import com.ivandsky.puzzlebox.gameui.GameUiRegistry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.PlayFlow
import org.koin.core.qualifier.qualifier
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
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

    /**
     * The full graph resolves for real under Robolectric: Room, Retrofit, and the
     * DataStore-backed preferences all construct without touching the network.
     */
    @Test
    fun `app module graph resolves at runtime`() {
        val koinApp = startKoin {
            androidContext(RuntimeEnvironment.getApplication())
            modules(PuzzleBoxApplication.appModules)
        }
        try {
            val koin = koinApp.koin
            assertNotNull(koin.get<DispatcherProvider>())
            assertNotNull(koin.get<PuzzleFeedApi>())
            assertNotNull(koin.get<PuzzleBoxDatabase>())
            assertNotNull(koin.get<DailyPuzzleRepository>())
            // Ticket 03: the Tango daily fetch bindings resolve (qualified archive seam).
            assertNotNull(koin.get<TangoDailySource>())
            assertNotNull(koin.get<TangoDailyRepository>())
            assertNotNull(
                koin.get<PuzzleSource>(qualifier = qualifier(GameId("tango").value)),
            )
            assertNotNull(koin.get<UserPreferencesRepository>())
            // Ticket 16: backend bindings resolve against the app-provided scope.
            assertNotNull(koin.get<IdentityService>())
            assertNotNull(koin.get<LeaderboardRepository>())
            assertNotNull(koin.get<GoogleIdTokenProvider>())
            // C1 regression: ProfileRoute's koinViewModel() needs this definition —
            // without it the first Profile composition throws NoDefinitionFound.
            assertNotNull(koin.get<ProfileViewModel>())

            // Game aggregation (ADR-0001): every game module's plugin lands in the
            // generic list, the app-level registry resolves, and the featured game's
            // renderer is registered — a missing entry is a wiring bug, loudly here.
            val plugins = koin.getAll<GamePlugin<*, *, *>>()
            assertTrue("expected at least one registered GamePlugin, got none", plugins.isNotEmpty())
            assertNotNull(koin.get<List<GamePlugin<*, *, *>>>())
            val registry = koin.get<GameUiRegistry>()
            assertNotNull("no board renderer registered for zip", registry[GameId("zip")])
            // Queens play wiring: the one shared flow resolves off the real graph (it takes
            // the queens repository) and the game's renderer is registered — a missing
            // line is a wiring bug.
            assertNotNull(koin.get<PlayFlow>())
            assertNotNull(koin.get<QueensDailyRepository>())
            assertNotNull("no board renderer registered for queens", registry[GameId("queens")])
        } finally {
            stopKoin() // close() alone leaves the JVM-global context registered for later tests
        }
    }
}
