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

    @Test
    fun `with TrainTango on the registry holds the Tango trainer`() {
        withGraph(FakeReleaseFlags(GameUiFlag.TrainTango)) { koin ->
            val registry = koin.get<TrainerRegistry>()

            assertEquals(listOf(GameId("tango")), registry.all.map { it.gameId })
            assertEquals(9, registry[GameId("tango")]!!.catalog().size)
            assertNull("Queens' flag is off", registry[GameId("queens")])
        }
    }

    @Test
    fun `with TrainTango off the registry has no Tango trainer though the game binds a holder`() {
        withGraph(FakeReleaseFlags(GameUiFlag.TrainQueens)) { koin ->
            val bindings = koin.getAll<TrainerGame.Binding>()

            assertNull(koin.get<TrainerRegistry>()[GameId("tango")])
            assertEquals("only Queens' holder holds a trainer", listOf(GameId("queens")), bindings.mapNotNull { it.game?.gameId })
            assertTrue("Tango binds an empty holder", bindings.any { it.game == null })
        }
    }

    @Test
    fun `another game's Train flag leaves the Tango trainer out`() {
        val others: Array<ReleaseFlagKey> = GameUiFlag.entries.filter { it != GameUiFlag.TrainTango }.toTypedArray()

        withGraph(FakeReleaseFlags(*others)) { koin ->
            assertNull(koin.get<TrainerRegistry>()[GameId("tango")])
        }
    }

    @Test
    fun `Queens and Tango both on list in the registry's game order`() {
        withGraph(FakeReleaseFlags(GameUiFlag.TrainTango, GameUiFlag.TrainQueens)) { koin ->
            assertEquals(listOf(GameId("queens"), GameId("tango")), koin.get<TrainerRegistry>().all.map { it.gameId })
        }
    }

    @Test
    fun `with TrainSudoku on the registry holds the Mini Sudoku trainer`() {
        withGraph(FakeReleaseFlags(GameUiFlag.TrainSudoku)) { koin ->
            val registry = koin.get<TrainerRegistry>()

            assertEquals(listOf(GameId("minisudoku")), registry.all.map { it.gameId })
            assertEquals(8, registry[GameId("minisudoku")]!!.catalog().size)
            assertNull("Queens' flag is off", registry[GameId("queens")])
            assertNull("Tango's flag is off", registry[GameId("tango")])
        }
    }

    @Test
    fun `with TrainSudoku off the registry has no Mini Sudoku trainer though the game binds a holder`() {
        withGraph(FakeReleaseFlags(GameUiFlag.TrainQueens, GameUiFlag.TrainTango)) { koin ->
            val bindings = koin.getAll<TrainerGame.Binding>()

            assertNull(koin.get<TrainerRegistry>()[GameId("minisudoku")])
            assertEquals(
                "only Queens' and Tango's holders hold a trainer",
                setOf(GameId("queens"), GameId("tango")),
                bindings.mapNotNull { it.game?.gameId }.toSet(),
            )
            assertEquals("Queens, Tango and Mini Sudoku each bind a holder", 3, bindings.size)
        }
    }

    @Test
    fun `another game's Train flag leaves the Mini Sudoku trainer out`() {
        val others: Array<ReleaseFlagKey> = GameUiFlag.entries.filter { it != GameUiFlag.TrainSudoku }.toTypedArray()

        withGraph(FakeReleaseFlags(*others)) { koin ->
            assertNull(koin.get<TrainerRegistry>()[GameId("minisudoku")])
        }
    }

    @Test
    fun `Queens, Tango and Mini Sudoku all on list in the registry's game order`() {
        withGraph(FakeReleaseFlags(GameUiFlag.TrainSudoku, GameUiFlag.TrainTango, GameUiFlag.TrainQueens)) { koin ->
            assertEquals(
                listOf(GameId("queens"), GameId("tango"), GameId("minisudoku")),
                koin.get<TrainerRegistry>().all.map { it.gameId },
            )
        }
    }

    @Test
    fun `classic Sudoku never has a trainer, whatever is on`() {
        withGraph(FakeReleaseFlags(*GameUiFlag.entries.toTypedArray<ReleaseFlagKey>())) { koin ->
            assertNull(koin.get<TrainerRegistry>()[GameId("sudoku")])
            assertTrue(koin.getAll<TrainerGame.Binding>().none { it.game?.gameId == GameId("sudoku") })
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
