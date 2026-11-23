package com.ivandsky.puzzlebox.app

import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.data.identity.IdentityService
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

/** Every game UI module's Trainer adapter reaches the [TrainerRegistry], in the registry's game order. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
class TrainerRegistryWiringTest {

    @Test
    fun `the registry holds the Queens, Tango and Mini Sudoku trainers in the game order`() {
        withGraph(FakeReleaseFlags()) { koin ->
            val registry = koin.get<TrainerRegistry>()

            assertEquals(listOf(GameId("queens"), GameId("tango"), GameId("minisudoku")), registry.all.map { it.gameId })
            assertEquals(10, registry[GameId("queens")]!!.catalog().size)
            assertEquals(9, registry[GameId("tango")]!!.catalog().size)
            assertEquals(8, registry[GameId("minisudoku")]!!.catalog().size)
        }
    }

    @Test
    fun `classic Sudoku never has a trainer`() {
        withGraph(FakeReleaseFlags()) { koin ->
            assertNull(koin.get<TrainerRegistry>()[GameId("sudoku")])
            assertTrue(koin.getAll<TrainerGame>().none { it.gameId == GameId("sudoku") })
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
