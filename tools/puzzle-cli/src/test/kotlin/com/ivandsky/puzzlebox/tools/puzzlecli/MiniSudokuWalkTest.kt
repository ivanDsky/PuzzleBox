package com.ivandsky.puzzlebox.tools.puzzlecli

import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import kotlin.random.Random
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniSudokuWalkTest {

    private val candidates: List<Puzzle> by lazy {
        val random = Random(11)
        List(600) { checkNotNull(MiniSudokuGenerator.next(random)) }
    }

    @Test
    fun `a board the listed techniques solve stays listed`() {
        assertTrue(candidates.any { MiniSudokuWalk.staysListed(it) })
    }

    @Test
    fun `a board that needs a technique the catalog does not list is turned away`() {
        assertTrue("every candidate solved with listed techniques", candidates.any { !MiniSudokuWalk.staysListed(it) })
    }

    @Test
    fun `a payload that does not decode and another game's puzzle are turned away`() {
        val garbage = Puzzle(SudokuGames.MINI.id, VariantId("sudoku/6x6"), 1, byteArrayOf(1, 2, 3))
        assertFalse(MiniSudokuWalk.staysListed(garbage))
        assertFalse(MiniSudokuWalk.staysListed(checkNotNull(QueensDrillGenerator.next(Random(1)))))
    }
}
