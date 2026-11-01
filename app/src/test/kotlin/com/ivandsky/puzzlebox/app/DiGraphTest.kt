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
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
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
            assertNotNull(koin.get<UserPreferencesRepository>())
            // Ticket 16: backend bindings resolve against the app-provided scope.
            assertNotNull(koin.get<IdentityService>())
            assertNotNull(koin.get<LeaderboardRepository>())
            assertNotNull(koin.get<GoogleIdTokenProvider>())
            // C1 regression: ProfileRoute's koinViewModel() needs this definition —
            // without it the first Profile composition throws NoDefinitionFound.
            assertNotNull(koin.get<ProfileViewModel>())
        } finally {
            koinApp.close()
        }
    }
}
