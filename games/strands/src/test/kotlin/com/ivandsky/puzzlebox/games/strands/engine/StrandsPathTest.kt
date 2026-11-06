package com.ivandsky.puzzlebox.games.strands.engine

import com.ivandsky.puzzlebox.games.strands.Coord
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrandsPathTest {

    private fun valid(vararg cells: Pair<Int, Int>) =
        StrandsPath.isValid(cells.map { Coord(it.first, it.second) }, rows = 8, cols = 6)

    @Test
    fun `empty and single-cell paths are valid`() {
        assertTrue(valid())
        assertTrue(valid(0 to 0))
    }

    @Test
    fun `all eight directions are adjacent`() {
        val around = listOf(-1 to -1, -1 to 0, -1 to 1, 0 to -1, 0 to 1, 1 to -1, 1 to 0, 1 to 1)
        around.forEach { (dr, dc) -> assertTrue("$dr,$dc", valid(3 to 3, 3 + dr to 3 + dc)) }
    }

    @Test
    fun `a cell is not adjacent to itself`() {
        assertFalse(Coord(2, 2).isAdjacentTo(Coord(2, 2)))
    }

    @Test
    fun `a skipped step is invalid`() {
        assertFalse(valid(0 to 0, 0 to 2))
        assertFalse(valid(0 to 0, 2 to 2))
    }

    @Test
    fun `reusing a cell is invalid`() {
        assertFalse(valid(0 to 0, 0 to 1, 1 to 1, 0 to 0))
        assertFalse(valid(0 to 0, 0 to 1, 0 to 0))
    }

    @Test
    fun `off-board cells are invalid`() {
        assertFalse(valid(-1 to 0))
        assertFalse(valid(7 to 5, 8 to 5))
        assertFalse(valid(0 to 5, 0 to 6))
        assertTrue(valid(7 to 5, 7 to 4))
    }
}
