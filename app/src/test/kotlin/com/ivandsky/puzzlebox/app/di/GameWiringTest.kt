package com.ivandsky.puzzlebox.app.di

import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.games.tango.tangoPlugin
import com.ivandsky.puzzlebox.games.zip.zipPlugin
import com.ivandsky.puzzlebox.games.zipui.ZipPlayUi
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
