package com.ivandsky.puzzlebox.data.di

import com.ivandsky.puzzlebox.data.db.PuzzleDailyDao
import com.ivandsky.puzzlebox.data.network.LinkedInTangoApi
import com.ivandsky.puzzlebox.data.network.LinkedInTangoClient
import com.ivandsky.puzzlebox.data.repository.LinkedInTangoDailySource
import com.ivandsky.puzzlebox.data.repository.TangoDailyRepository
import com.ivandsky.puzzlebox.data.repository.TangoDailySource
import com.ivandsky.puzzlebox.common.di.commonModule
import com.ivandsky.puzzlebox.game.PuzzleSource
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.qualifier
import org.koin.dsl.koinApplication
import org.koin.test.KoinTest
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The ticket-03 slice of the DI graph resolves for real under Robolectric: Room, the two
 * LinkedIn Retrofits, and the Tango daily bindings construct without touching the
 * network. (The full app graph lives in :app's DiGraphTest; ticket 04's in-flight UI
 * module can block that compile, so the data slice keeps its own guard here.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataModuleTangoGraphTest : KoinTest {

    @Test
    fun `tango daily fetch bindings resolve at runtime`() {
        val koinApp = koinApplication {
            androidContext(RuntimeEnvironment.getApplication())
            modules(commonModule, dataModule)
        }
        try {
            val koin = koinApp.koin
            assertNotNull(koin.get<LinkedInTangoApi>())
            assertNotNull(koin.get<LinkedInTangoClient>())
            assertNotNull(koin.get<LinkedInTangoDailySource>())
            assertNotNull(koin.get<TangoDailySource>())
            assertNotNull(koin.get<TangoDailyRepository>())
            // The qualified archive seam: the Zip binding stays unqualified, Tango rides
            // the game-id qualifier, so neither overwrites the other.
            assertNotNull(koin.get<PuzzleSource>(qualifier = qualifier(GameId("tango").value)))
            assertNotNull(koin.get<PuzzleSource>())
            assertNotNull(koin.get<com.ivandsky.puzzlebox.data.db.PuzzleDailyDao>())
        } finally {
            koinApp.close()
        }
    }
}
