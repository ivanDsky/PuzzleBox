package com.ivandsky.puzzlebox.app

import com.ivandsky.puzzlebox.game.DailySource
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Release keeps the debug-only 9x9 game and its editor out of the graph (ADR-0015). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
class ReleaseGameModulesTest {

    @Test
    fun `release graph serves minisudoku but not sudoku`() {
        assertTrue(buildTypeGameModules.isEmpty())
        val koin = startKoin {
            androidContext(RuntimeEnvironment.getApplication())
            modules(PuzzleBoxApplication.appModules)
        }.koin
        try {
            // GameRegistry's definition runs requireEveryGamePlayable; resolving it is the check.
            val games = koin.get<GameRegistry>().all.map { it.definition.id.value }
            assertEquals(listOf("zip", "tango", "queens", "strands", "minisudoku"), games)
            val sources = koin.getAll<DailySource>().map { it.gameId }.toSet()
            assertTrue(GameId("minisudoku") in sources)
            assertTrue(GameId("sudoku") !in sources)
            val editors = koin.get<GameEditorRegistry>()
            assertNotNull(editors[GameId("minisudoku")])
            assertNull(editors[GameId("sudoku")])
        } finally {
            stopKoin()
        }
    }
}
