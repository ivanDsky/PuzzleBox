package com.ivandsky.puzzlebox.games.sudokuui

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.games.sudoku.SudokuDefinition
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.hint.HintMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.koin.core.Koin
import org.koin.core.qualifier.qualifier
import org.koin.dsl.koinApplication
import org.koin.core.annotation.KoinInternalApi
import org.koin.dsl.module

class SudokuHintModeWiringTest {

    private val env = PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), {}, {})

    // The Sudoku modules are top-level vals and Koin caches each singleton on its factory, so a play UI built
    // by another test (or test class) would leak in. Drop those instances before and after each test.
    private val koins = mutableListOf<Koin>()

    private val defaultDispatcher = Dispatchers.Unconfined

    private fun koinWith(): Koin = koinApplication {
        modules(miniSudokuUiModule, sudokuUiModule, module {
            single<DispatcherProvider> {
                object : DispatcherProvider {
                    override val main = Dispatchers.Default
                    override val io = Dispatchers.IO
                    override val default = defaultDispatcher
                }
            }
            single<SudokuHighlightSettings> { InMemorySudokuHighlightSettings() }
        })
    }.koin.also { koins += it }

    @OptIn(KoinInternalApi::class)
    private fun dropCachedSingles() {
        listOf(miniSudokuUiModule, sudokuUiModule).forEach { module -> module.mappings.values.forEach { it.dropAll() } }
    }

    @Before
    fun dropBefore() = dropCachedSingles()

    @After
    fun closeKoins() {
        koins.forEach { it.close() }
        dropCachedSingles()
    }

    private fun playUi(koin: Koin, definition: SudokuDefinition) =
        koin.get<GamePlayUi>(qualifier(definition.id.value))

    @Test
    fun `the mode source is invoked exactly once per controller and the controller carries its value`() {
        var calls = 0
        val stepwise = HintMode.Stepwise(Dispatchers.Unconfined)
        val ui = SudokuPlayUi(SudokuGames.MINI, InMemorySudokuHighlightSettings(), MiniSudokuBrand) { calls++; stepwise }

        val controller = ui.createController(env) as SudokuPlayController

        assertEquals(1, calls)
        assertEquals(stepwise, controller.hintMode)
    }

    @Test
    fun `mini resolves stepwise on the provider's default dispatcher and classic resolves legacy`() {
        val koin = koinWith()

        val mini = playUi(koin, SudokuGames.MINI).createController(env) as SudokuPlayController
        val classic = playUi(koin, SudokuGames.CLASSIC).createController(env) as SudokuPlayController

        assertEquals(HintMode.Stepwise(defaultDispatcher), mini.hintMode)
        assertEquals(HintMode.Legacy, classic.hintMode)
    }
}
