package com.ivandsky.puzzlebox.app.di

import com.ivandsky.puzzlebox.app.buildTypeGameModules
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.gameui.editor.GameEditorUi
import com.ivandsky.puzzlebox.gameui.hint.HintMode
import com.ivandsky.puzzlebox.gameui.hint.HintModeSource
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudokuui.miniSudokuUiModule
import com.ivandsky.puzzlebox.games.tango.tangoPlugin
import com.ivandsky.puzzlebox.games.zip.zipPlugin
import com.ivandsky.puzzlebox.games.zipui.ZipPlayUi
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/** ADR-0008 "Adding a game": a plugin wired without its play UI fails loudly, naming it. */
class GameWiringTest {

    @Test
    fun `a game plugin without a play ui fails fast naming its id`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            requireEveryGamePlayable(GameRegistry(listOf(zipPlugin, tangoPlugin)), GamePlayRegistry(listOf(ZipPlayUi())))
        }
        assertTrue(error.message!!, error.message!!.contains("'tango'"))
    }

    @Test
    fun `an unlisted game without a play ui also fails fast`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            requireEveryGamePlayable(
                GameRegistry(listOf(zipPlugin, tangoPlugin), unlisted = setOf(tangoPlugin.definition.id)),
                GamePlayRegistry(listOf(ZipPlayUi())),
            )
        }
        assertTrue(error.message!!, error.message!!.contains("'tango'"))
    }

    @Test
    fun `debug has a 9x9 editor beside mini`() {
        val koin = koinApplication {
            modules(listOf(miniSudokuUiModule, module { single { HintModeSource { HintMode.Legacy } } }) + buildTypeGameModules)
        }.koin
        val editors = GameEditorRegistry(koin.getAll<GameEditorUi>())
        assertNotNull(editors[SudokuGames.MINI.id])
        assertNotNull(editors[SudokuGames.CLASSIC.id])
    }
}
