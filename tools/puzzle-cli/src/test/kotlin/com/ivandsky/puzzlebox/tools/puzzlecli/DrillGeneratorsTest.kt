package com.ivandsky.puzzlebox.tools.puzzlecli

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.codec.QueensCodec
import com.ivandsky.puzzlebox.games.queens.engine.solve.QueensSolver
import com.ivandsky.puzzlebox.games.queens.engine.trainer.QueensTechniqueWalker
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.codec.SudokuCodec
import com.ivandsky.puzzlebox.games.sudoku.engine.solve.SudokuSolver
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuTechniqueWalker
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tango.codec.TangoCodec
import com.ivandsky.puzzlebox.games.tango.engine.TangoUniqueness
import com.ivandsky.puzzlebox.games.tango.engine.trainer.TangoTechniqueWalker
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three generators on their own: every puzzle decodes through its game's definition, has exactly one solution,
 * and is something the game's walker can walk; the same seed gives the same puzzles and another seed gives others.
 */
class DrillGeneratorsTest {

    private fun draw(generator: DrillGenerator, seed: Long, count: Int): List<Puzzle> {
        val random = Random(seed)
        return List(count) { checkNotNull(generator.next(random)) { "the generator gave no puzzle for seed $seed" } }
    }

    private fun <T> ok(result: DecodeResult<T>): T = (result as DecodeResult.Ok).value

    @Test
    fun `queens puzzles are uniquely solvable boards the walker counts steps of`() {
        for (puzzle in draw(QueensDrillGenerator, seed = 5, count = 6)) {
            assertEquals(QueensDefinition.id, puzzle.gameId)
            val decoded = ok(QueensCodec.decode(puzzle.payload))
            assertTrue("board size ${decoded.size}", decoded.size in QueensDrillGenerator.SIZES)
            assertNotNull("a stored solution", decoded.solution)
            assertEquals(1, QueensSolver().countSolutions(decoded, 2))
            assertTrue(QueensTechniqueWalker.techniqueCounts(puzzle).isNotEmpty())
        }
    }

    @Test
    fun `tango puzzles are uniquely solvable 6 by 6 boards the walker counts steps of`() {
        for (puzzle in draw(TangoDrillGenerator, seed = 5, count = 6)) {
            assertEquals(TangoDefinition.id, puzzle.gameId)
            val decoded = ok(TangoCodec.decode(puzzle.payload))
            assertEquals(6, decoded.n)
            assertEquals(1, TangoUniqueness.countSolutions(decoded, 2))
            assertTrue(TangoTechniqueWalker.techniqueCounts(puzzle).isNotEmpty())
        }
    }

    @Test
    fun `mini sudoku puzzles are uniquely solvable and minimal, and the walker counts steps of them`() {
        for (puzzle in draw(MiniSudokuGenerator, seed = 5, count = 12)) {
            assertEquals(SudokuGames.MINI.id, puzzle.gameId)
            val decoded = ok(SudokuCodec.decode(puzzle.payload))
            val geometry = decoded.geometry
            assertEquals(1, SudokuSolver.solve(geometry, decoded.givens, 2).count)
            for (idx in decoded.givens.indices.filter { decoded.givens[it] != 0 }) {
                val without = decoded.givens.toMutableList().also { it[idx] = 0 }
                assertTrue("given $idx is not needed", SudokuSolver.solve(geometry, without, 2).count != 1)
            }
            assertTrue(SudokuTechniqueWalker.techniqueCounts(puzzle).isNotEmpty())
        }
    }

    @Test
    fun `the same seed gives the same puzzles and another seed gives others`() {
        for (generator in listOf(QueensDrillGenerator, TangoDrillGenerator, MiniSudokuGenerator)) {
            val again = draw(generator, seed = 3, count = 3)
            assertEquals(again, draw(generator, seed = 3, count = 3))
            assertNotEquals(again, draw(generator, seed = 4, count = 3))
        }
    }
}
