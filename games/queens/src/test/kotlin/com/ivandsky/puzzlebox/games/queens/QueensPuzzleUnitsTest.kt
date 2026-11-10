package com.ivandsky.puzzlebox.games.queens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** The per-puzzle row, column and region cell tables the hint rungs share. */
class QueensPuzzleUnitsTest {

    /** 4×4 with ragged regions: region 0 is the top-left L, region 3 the bottom-right corner. */
    private val regions = listOf(
        0, 0, 1, 1,
        0, 2, 2, 1,
        0, 2, 3, 3,
        2, 2, 3, 3,
    )
    private val puzzle = QueensPuzzle(4, regions)

    @Test
    fun `rows, columns and regions list their cells in ascending order`() {
        assertEquals(listOf(listOf(0, 1, 2, 3), listOf(4, 5, 6, 7), listOf(8, 9, 10, 11), listOf(12, 13, 14, 15)), puzzle.rowCells)
        assertEquals(listOf(listOf(0, 4, 8, 12), listOf(1, 5, 9, 13), listOf(2, 6, 10, 14), listOf(3, 7, 11, 15)), puzzle.columnCells)
        assertEquals(listOf(listOf(0, 1, 4, 8), listOf(2, 3, 7), listOf(5, 6, 9, 12, 13), listOf(10, 11, 14, 15)), puzzle.regionCells)
    }

    @Test
    fun `each table is derived once per puzzle`() {
        assertSame(puzzle.rowCells, puzzle.rowCells)
        assertSame(puzzle.columnCells, puzzle.columnCells)
        assertSame(puzzle.regionCells, puzzle.regionCells)
    }

    @Test
    fun `a built table changes neither equality nor hash code`() {
        val used = QueensPuzzle(4, regions)
        used.rowCells; used.columnCells; used.regionCells
        assertEquals(puzzle, used)
        assertEquals(puzzle.hashCode(), used.hashCode())
    }

    @Test
    fun `a copy with other regions builds its own regions table`() {
        puzzle.regionCells
        val rows = puzzle.copy(regions = List(16) { it / 4 })
        assertEquals(puzzle.rowCells, rows.regionCells)
    }
}
