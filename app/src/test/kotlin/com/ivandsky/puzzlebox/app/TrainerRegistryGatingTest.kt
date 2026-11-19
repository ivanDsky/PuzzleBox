package com.ivandsky.puzzlebox.app

import com.ivandsky.puzzlebox.common.flags.ReleaseFlagKey
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.gameui.flags.GameUiFlag
import com.ivandsky.puzzlebox.gameui.trainer.TrainerGame
import com.ivandsky.puzzlebox.gameui.trainer.TrainerRegistry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A game's Trainer adapter reaches the [TrainerRegistry] only while its Train flag is on: the game UI module binds a
 * [TrainerGame.Binding] either way, and the registry collects the ones that hold an adapter.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
class TrainerRegistryGatingTest {

    @Test
    fun `with TrainQueens on the registry holds the Queens trainer`() {
        withGraph(FakeReleaseFlags(GameUiFlag.TrainQueens)) { koin ->
            val registry = koin.get<TrainerRegistry>()

            assertEquals(listOf(GameId("queens")), registry.all.map { it.gameId })
            assertEquals(10, registry[GameId("queens")]!!.catalog().size)
        }
    }

    @Test
    fun `with TrainQueens off the registry is empty though the game binds a holder`() {
        withGraph(FakeReleaseFlags()) { koin ->
            val registry = koin.get<TrainerRegistry>()
            val bindings = koin.getAll<TrainerGame.Binding>()

            assertTrue(registry.isEmpty)
            assertNull(registry[GameId("queens")])
            assertTrue("Queens binds a holder either way", bindings.isNotEmpty())
            assertTrue("the holder is empty", bindings.all { it.game == null })
        }
    }

    @Test
    fun `another game's Train flag leaves the Queens trainer out`() {
        val others: Array<ReleaseFlagKey> = GameUiFlag.entries.filter { it != GameUiFlag.TrainQueens }.toTypedArray()

        withGraph(FakeReleaseFlags(*others)) { koin ->
            assertNull(koin.get<TrainerRegistry>()[GameId("queens")])
        }
    }

    private fun withGraph(flags: ReleaseFlags, check: (Koin) -> Unit) {
        val koin = startKoin {
            androidContext(RuntimeEnvironment.getApplication())
            modules(
                PuzzleBoxApplication.appModules + module {
                    single<IdentityService> { FakeIdentityService() }
                    single<ReleaseFlags> { flags }
                },
            )
        }.koin
        try {
            check(koin)
        } finally {
            stopKoin()
        }
    }
}
