package com.ivandsky.puzzlebox.games.sudoku.engine.solve

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.games.sudoku.KnownSolutions
import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.SudokuGeometry
import com.ivandsky.puzzlebox.games.sudoku.SudokuVariants
import com.ivandsky.puzzlebox.games.sudoku.editor.SudokuTestDrafts
import com.ivandsky.puzzlebox.games.sudoku.engine.rules.SudokuConflicts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SudokuRandomFillTest {

    private val geometries = SudokuVariants.all.map { it.geometry }

    private fun fill(geometry: SudokuGeometry, givens: List<Int>, seed: Int, budget: Budget = Budget.Unlimited): List<Int>? =
        SudokuRandomFill.fill(geometry, givens, Random(seed), budget)

    private fun blank(geometry: SudokuGeometry): List<Int> = List(geometry.cellCount) { 0 }

    private fun isSolved(geometry: SudokuGeometry, grid: List<Int>): Boolean =
        grid.all { it != 0 } && SudokuConflicts.isSolved(SudokuBoard(geometry, grid.map { SudokuCell.Given(it) }))

    @Test
    fun `a blank board fills to a solved grid at both sizes`() {
        for (geometry in geometries) {
            val grid = checkNotNull(fill(geometry, blank(geometry), seed = 1)) { "no grid at size ${geometry.size}" }

            assertTrue("$grid", isSolved(geometry, grid))
        }
    }

    @Test
    fun `a board with one solution fills to that solution`() {
        for (puzzle in listOf(KnownSolutions.miniSample, KnownSolutions.classicSample)) {
            assertEquals(puzzle.solution, fill(puzzle.geometry, puzzle.givens, seed = 1))
        }
    }

    @Test
    fun `the same seed fills the same grid`() {
        for (geometry in geometries) {
            val first = checkNotNull(fill(geometry, blank(geometry), seed = 7)) { "no grid at size ${geometry.size}" }

            assertEquals(first, fill(geometry, blank(geometry), seed = 7))
        }
    }

    @Test
    fun `another seed fills another grid`() {
        for (geometry in geometries) {
            val first = checkNotNull(fill(geometry, blank(geometry), seed = 7)) { "no grid at size ${geometry.size}" }

            assertNotEquals(first, fill(geometry, blank(geometry), seed = 8))
        }
    }

    @Test
    fun `givens that clash have no fill and start no search`() {
        for (geometry in geometries) {
            val clashInRow = List(geometry.cellCount) { if (it == 0 || it == geometry.size - 1) 3 else 0 }
            var polls = 0

            assertNull(fill(geometry, clashInRow, seed = 1, Budget { ++polls >= SPENT_AT }))
            assertEquals("size ${geometry.size}", 0, polls)
        }
    }

    @Test
    fun `scattered givens fill exactly when the solver finds a solution and the grid keeps them`() {
        for (geometry in geometries) {
            var filled = 0
            var refused = 0
            for (seed in 0 until SCATTERED) {
                val givens = SudokuTestDrafts.scattered(geometry, geometry.cellCount / 4 + seed % 8, Random(seed)).givens
                val name = "size ${geometry.size} seed $seed"

                val grid = fill(geometry, givens, seed)

                assertEquals(name, SudokuSolver.solve(geometry, givens, limit = 1).count == 1, grid != null)
                if (grid == null) {
                    refused++
                } else {
                    filled++
                    assertTrue("$name: $grid", isSolved(geometry, grid))
                    assertEquals(name, givens, grid.mapIndexed { idx, digit -> if (givens[idx] == 0) 0 else digit })
                }
            }
            assertTrue("size ${geometry.size}: $filled filled, $refused refused", filled > 0 && refused > 0)
        }
    }

    @Test
    fun `a budget spent in the search stops it within a poll`() {
        for (geometry in geometries) {
            var polls = 0
            val budget = Budget { ++polls >= SPENT_AT }

            assertNull(fill(geometry, blank(geometry), seed = 1, budget))
            assertEquals(SPENT_AT, polls)
        }
    }

    @Test
    fun `a draw that sends the search astray is drawn again`() {
        // Random(304)'s first digits leave these givens no grid, which the search finds out only
        // after millions of nodes: found by the completer's measurement, where it took a second.
        val geometry = SudokuVariants.CLASSIC.geometry
        val givens = KnownSolutions.grid("000000000000000800000000500003800000090000003000000000000000000000000000010000030")
        var polls = 0

        val grid = fill(geometry, givens, seed = 304, Budget { ++polls > ASTRAY_POLLS })

        assertTrue("$grid after $polls polls", grid != null && isSolved(geometry, grid))
        assertEquals(givens, grid!!.mapIndexed { idx, digit -> if (givens[idx] == 0) 0 else digit })
    }

    @Test
    fun `givens with no grid are refused when the search had to start over`() {
        val geometry = SudokuVariants.CLASSIC.geometry
        val givens = SudokuTestDrafts.scattered(geometry, count = 17, Random(44)).givens
        assertEquals(0, SudokuSolver.solve(geometry, givens, limit = 1).count)
        var polls = 0

        val grid = fill(geometry, givens, seed = 44, Budget { ++polls > ASTRAY_POLLS * 10 })

        assertNull("$polls polls", grid)
        assertTrue("$polls polls", polls in SudokuRandomFill.FIRST_DRAW_NODES + 1..ASTRAY_POLLS * 10)
    }

    private companion object {
        const val SCATTERED = 60

        /** Fewer nodes than a blank 6×6 has cells, so the search is cut short. */
        const val SPENT_AT = 10

        /** Polls a fill stays well under once a search gone astray is cut short. */
        const val ASTRAY_POLLS = 100_000
    }
}
