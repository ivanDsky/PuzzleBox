package com.ivandsky.puzzlebox.games.sudoku

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuGeometryTest {

    private val mini = SudokuVariants.MINI.geometry
    private val classic = SudokuVariants.CLASSIC.geometry

    @Test
    fun `every cell has 12 peers on 6x6 and 20 on 9x9, ascending and excluding itself`() {
        for ((geometry, expected) in listOf(mini to 12, classic to 20)) {
            for (idx in 0 until geometry.cellCount) {
                val peers = geometry.peersOf(idx)
                assertEquals(expected, peers.size)
                assertFalse(idx in peers)
                assertTrue(peers.toList().zipWithNext().all { (a, b) -> a < b })
            }
        }
    }

    @Test
    fun `units run rows then columns then boxes`() {
        assertEquals(18, mini.units.size)
        assertEquals(27, classic.units.size)
        assertEquals(List(6) { SudokuUnit(UnitKind.ROW, it) }, mini.units.take(6))
        assertEquals(SudokuUnit(UnitKind.COLUMN, 0), mini.units[6])
        assertEquals(SudokuUnit(UnitKind.BOX, 5), mini.units.last())
    }

    @Test
    fun `cellsOf lists each unit ascending`() {
        assertArrayEquals(intArrayOf(6, 7, 8, 9, 10, 11), mini.cellsOf(SudokuUnit(UnitKind.ROW, 1)))
        assertArrayEquals(intArrayOf(2, 8, 14, 20, 26, 32), mini.cellsOf(SudokuUnit(UnitKind.COLUMN, 2)))
        assertArrayEquals(intArrayOf(15, 16, 17, 21, 22, 23), mini.cellsOf(SudokuUnit(UnitKind.BOX, 3)))
        assertArrayEquals(intArrayOf(60, 61, 62, 69, 70, 71, 78, 79, 80), classic.cellsOf(SudokuUnit(UnitKind.BOX, 8)))
    }

    @Test
    fun `boxOf counts boxes row-major and agrees with cellsOf`() {
        assertEquals(0, mini.boxOf(8))
        assertEquals(1, mini.boxOf(3))
        assertEquals(3, mini.boxOf(23))
        assertEquals(5, mini.boxOf(35))
        for (geometry in listOf(mini, classic)) {
            for (box in 0 until geometry.size) {
                for (idx in geometry.cellsOf(SudokuUnit(UnitKind.BOX, box))) assertEquals(box, geometry.boxOf(idx))
            }
        }
    }

    @Test
    fun `boxCells are the regions LinkedIn sends for #450`() {
        val regions = listOf(
            listOf(0, 1, 2, 6, 7, 8), listOf(3, 4, 5, 9, 10, 11), listOf(12, 13, 14, 18, 19, 20),
            listOf(15, 16, 17, 21, 22, 23), listOf(24, 25, 26, 30, 31, 32), listOf(27, 28, 29, 33, 34, 35),
        )
        assertEquals(regions, mini.boxCells().map { it.toList() })
    }

    @Test
    fun `boxes that do not tile the board are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { SudokuGeometry(6, 3, 3) }
        assertThrows(IllegalArgumentException::class.java) { SudokuGeometry(0, 0, 0) }
    }

    @Test
    fun `equality is by shape`() {
        assertEquals(SudokuGeometry(6, 2, 3), mini)
        assertEquals(SudokuGeometry(6, 2, 3).hashCode(), mini.hashCode())
    }
}
