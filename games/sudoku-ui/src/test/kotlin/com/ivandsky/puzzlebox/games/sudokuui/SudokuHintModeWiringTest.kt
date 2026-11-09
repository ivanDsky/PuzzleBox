package com.ivandsky.puzzlebox.games.sudokuui

import com.ivandsky.puzzlebox.games.sudoku.SudokuDefinition
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.hint.HintMode
import com.ivandsky.puzzlebox.gameui.hint.HintModeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.koin.core.Koin
import org.koin.core.qualifier.qualifier
import org.koin.dsl.koinApplication
import org.koin.core.annotation.KoinInternalApi
import org.koin.dsl.module

class SudokuHintModeWiringTest {

    private val env = PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), {}, {})

    // The Sudoku modules are top-level vals and Koin caches each singleton on its factory, so a play UI built
    // under one source would leak into the next test. Drop those instances along with the Koin graphs.
    private val koins = mutableListOf<Koin>()

    private fun koinWith(source: HintModeSource): Koin = koinApplication {
        modules(miniSudokuUiModule, sudokuUiModule, module { single { source } })
    }.koin.also { koins += it }

    @After
    @OptIn(KoinInternalApi::class)
    fun closeKoins() {
        koins.forEach { it.close() }
        listOf(miniSudokuUiModule, sudokuUiModule).forEach { module -> module.mappings.values.forEach { it.dropAll() } }
    }

    private fun playUi(koin: Koin, definition: SudokuDefinition) =
        koin.get<GamePlayUi>(qualifier(definition.id.value))

    @Test
    fun `the mode source is invoked exactly once per controller and the controller carries its value`() {
        var calls = 0
        val stepwise = HintMode.Stepwise(Dispatchers.Unconfined)
        val ui = SudokuPlayUi(SudokuGames.MINI, MiniSudokuBrand) { calls++; stepwise }

        val controller = ui.createController(env) as SudokuPlayController

        assertEquals(1, calls)
        assertEquals(stepwise, controller.hintMode)
    }

    @Test
    fun `without a mode mini and classic create legacy controllers`() {
        val mini = SudokuPlayUi(SudokuGames.MINI).createController(env) as SudokuPlayController
        val classic = SudokuPlayUi(SudokuGames.CLASSIC).createController(env) as SudokuPlayController

        assertEquals(HintMode.Legacy, mini.hintMode)
        assertEquals(HintMode.Legacy, classic.hintMode)
    }

    @Test
    fun `a bound stepwise source reaches mini but never classic`() {
        val koin = koinWith(HintModeSource { HintMode.Stepwise(Dispatchers.Unconfined) })

        val mini = playUi(koin, SudokuGames.MINI).createController(env) as SudokuPlayController
        val classic = playUi(koin, SudokuGames.CLASSIC).createController(env) as SudokuPlayController

        assertEquals(HintMode.Stepwise(Dispatchers.Unconfined), mini.hintMode)
        assertEquals(HintMode.Legacy, classic.hintMode)
    }

    @Test
    fun `a bound legacy source keeps both games legacy`() {
        val koin = koinWith(HintModeSource { HintMode.Legacy })

        val mini = playUi(koin, SudokuGames.MINI).createController(env) as SudokuPlayController
        val classic = playUi(koin, SudokuGames.CLASSIC).createController(env) as SudokuPlayController

        assertEquals(HintMode.Legacy, mini.hintMode)
        assertEquals(HintMode.Legacy, classic.hintMode)
    }
}
