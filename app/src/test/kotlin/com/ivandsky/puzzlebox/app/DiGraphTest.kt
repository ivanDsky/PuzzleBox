package com.ivandsky.puzzlebox.app

import com.ivandsky.puzzlebox.common.di.commonModule
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.db.PuzzleBoxDatabase
import com.ivandsky.puzzlebox.data.network.PuzzleFeedApi
import com.ivandsky.puzzlebox.data.repository.DailyPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.gameui.GameUiRegistry
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
@Config(sdk = [34])
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

            // Game aggregation (ADR-0001): every game module's plugin lands in the
            // generic list, the app-level registry resolves, and the featured game's
            // renderer is registered — a missing entry is a wiring bug, loudly here.
            val plugins = koin.getAll<GamePlugin<*, *, *>>()
            assertTrue("expected at least one registered GamePlugin, got none", plugins.isNotEmpty())
            assertNotNull(koin.get<List<GamePlugin<*, *, *>>>())
            val registry = koin.get<GameUiRegistry>()
            assertNotNull("no board renderer registered for zip", registry[GameId("zip")])
        } finally {
            koinApp.close()
        }
    }
}
