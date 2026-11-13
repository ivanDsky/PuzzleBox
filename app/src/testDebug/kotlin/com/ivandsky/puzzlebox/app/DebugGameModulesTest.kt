package com.ivandsky.puzzlebox.app

import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Debug ships the 9x9 unlisted: every surface's five games, the 9x9 reachable by id (ADR-0015). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DrainTestApplication::class)
class DebugGameModulesTest {

    @Test
    fun `debug graph lists five games and keeps sudoku unlisted but resolvable`() {
        val koin = startKoin {
            androidContext(RuntimeEnvironment.getApplication())
            modules(PuzzleBoxApplication.appModules)
        }.koin
        try {
            // GameRegistry's definition runs requireEveryGamePlayable; resolving it is the check.
            val games = koin.get<GameRegistry>()
            assertEquals(listOf("zip", "tango", "queens", "strands", "minisudoku"), games.all.map { it.definition.id.value })
            assertEquals(listOf("sudoku"), games.unlisted.map { it.definition.id.value })
            assertEquals(GameId("sudoku"), games[GameId("sudoku")]?.definition?.id)
        } finally {
            stopKoin()
        }
    }
}
