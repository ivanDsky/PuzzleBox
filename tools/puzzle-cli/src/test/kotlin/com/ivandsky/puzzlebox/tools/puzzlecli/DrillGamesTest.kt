package com.ivandsky.puzzlebox.tools.puzzlecli

import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.queensPlugin
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrillGamesTest {

    @Test
    fun `the registry gives Queens, Tango and Mini Sudoku in that order with every technique of their catalogs`() {
        val games = DrillGames.of(PuzzleCli.registry)

        assertEquals(listOf(QueensDefinition.id, TangoDefinition.id, SudokuGames.MINI.id), games.map { it.gameId })
        assertEquals(listOf(10, 9, 8), games.map { it.techniques.size })
        for (game in games) assertEquals("no repeated technique id in ${game.gameId.value}", game.techniques.size, game.techniques.toSet().size)
    }

    @Test
    fun `only Mini Sudoku caps a drill at its own family`() {
        val (queens, tango, mini) = DrillGames.of(PuzzleCli.registry)

        assertTrue(queens.families.isEmpty() && tango.families.isEmpty())
        assertEquals(mini.techniques.toSet(), mini.families.keys)
    }

    @Test
    fun `a game the registry does not hold is left out`() {
        val registry = GameRegistry(listOf(queensPlugin))

        assertEquals(listOf(QueensDefinition.id), DrillGames.of(registry).map { it.gameId })
    }
}
