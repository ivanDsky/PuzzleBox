package com.ivandsky.puzzlebox.data.di

import com.ivandsky.puzzlebox.common.di.commonModule
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.testing.game.FakeDailySource
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.game.TestGames.alpha
import com.ivandsky.puzzlebox.database.PuzzleDailyDao
import com.ivandsky.puzzlebox.game.DailySource
import com.ivandsky.puzzlebox.game.FeedTransport
import com.ivandsky.puzzlebox.game.GameRegistry
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The data slice of the DI graph resolves for real under Robolectric: Room, the one
 * LinkedIn Retrofit + transport, and the generic daily/completion repositories construct
 * without touching the network, given what :app supplies (a [GameRegistry] and the game
 * modules' [DailySource]s). The full app graph lives in :app's DiGraphTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataModuleGraphTest : KoinTest {

    @Test
    fun `generic daily and network bindings resolve at runtime`() {
        val games = module {
            single { TestGames.registry(listOf(alpha)) }
            single { FakeDailySource(alpha) } bind DailySource::class
        }
        val koinApp = koinApplication {
            androidContext(RuntimeEnvironment.getApplication())
            modules(commonModule, dataModule, games)
        }
        try {
            val koin = koinApp.koin
            assertNotNull(koin.get<FeedTransport>())
            assertNotNull(koin.get<DailyRepository>())
            assertNotNull(koin.get<CompletionRepository>())
            assertNotNull(koin.get<PuzzleDailyDao>())
        } finally {
            koinApp.close()
        }
    }
}
