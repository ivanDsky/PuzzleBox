package com.ivandsky.puzzlebox.designsystem.recap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** The Home and hub tiles' mosaic: always a full 25 cells, split by share, and the same for the same period. */
class RecapMosaicTest {

    private fun counts(cells: List<String>) = cells.groupingBy { it }.eachCount()

    @Test
    fun `cells always total 25 whatever the split`() {
        listOf(
            listOf("zip" to 1, "tango" to 1, "queens" to 1),
            listOf("zip" to 96, "tango" to 17, "queens" to 5, "sudoku" to 3, "strands" to 1),
            listOf("zip" to 7, "tango" to 7, "queens" to 7, "sudoku" to 7),
        ).forEach { assertEquals(MOSAIC_CELLS, recapMosaic(it, "2026-10").size) }
    }

    @Test
    fun `a lone game fills the grid and a game with no solves gets no cell`() {
        assertEquals(mapOf("zip" to 25), counts(recapMosaic(listOf("zip" to 3, "tango" to 0), "2026-10")))
    }

    @Test
    fun `cells follow the share of solves, the leftover going to the largest remainders`() {
        // 25 × (50, 30, 20)/100 = 12.5, 7.5, 5: the two halves tie, and the earlier game takes the spare cell.
        assertEquals(mapOf("a" to 13, "b" to 7, "c" to 5), counts(recapMosaic(listOf("a" to 50, "b" to 30, "c" to 20), "x")))
    }

    @Test
    fun `nothing solved is no mosaic`() {
        assertEquals(emptyList<String>(), recapMosaic(emptyList<Pair<String, Int>>(), "2026-10"))
        assertEquals(emptyList<String>(), recapMosaic(listOf("zip" to 0), "2026-10"))
    }

    @Test
    fun `the same period draws the same mosaic and another period a different one`() {
        val shares = listOf("zip" to 40, "tango" to 30, "queens" to 20, "sudoku" to 6)

        assertEquals(recapMosaic(shares, "2026-10"), recapMosaic(shares, "2026-10"))
        assertNotEquals(recapMosaic(shares, "2026-10"), recapMosaic(shares, "2026-09"))
    }
}
