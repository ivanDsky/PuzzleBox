package com.ivandsky.puzzlebox.app

import android.app.Application
import com.ivandsky.puzzlebox.app.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.app.screens.ProfileViewModel
import com.ivandsky.puzzlebox.common.di.commonModule
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.database.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.game.DailySource
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.PlaySessionStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
            assertNotNull(koin.get<PuzzleBoxDatabase>())
            // The generic daily stack: one repository over every game's DailySource.
            assertNotNull(koin.get<DailyRepository>())
            assertEquals(
                setOf(GameId("zip"), GameId("tango"), GameId("queens")),
                koin.getAll<DailySource>().map { it.gameId }.toSet(),
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
            // registry in picker order, and every game's play UI is registered — a
            // missing entry is a wiring bug, loudly here.
            val games = koin.get<GameRegistry>().all.map { it.definition.id.value }
            assertEquals(listOf("zip", "tango", "queens"), games)
            val playUis = koin.get<GamePlayRegistry>()
            for (id in games) assertNotNull("no play UI registered for $id", playUis[GameId(id)])
            // The one shared play session store resolves off the real graph.
            assertNotNull(koin.get<PlaySessionStore>())
        } finally {
            stopKoin() // close() alone leaves the JVM-global context registered for later tests
        }
    }
}
