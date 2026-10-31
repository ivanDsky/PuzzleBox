package com.ivandsky.puzzlebox.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

// The pure board geometry seam: screen position -> (row, col). Row 0 is the top,
// col 0 the left. Out-of-bounds positions clamp to the edge cells so a drag that
// leaves the board still reports a valid path end.
class PuzzleBoardMappingTest {

    private val side = 400f

    @Test
    fun `top left position maps to row 0 col 0`() {
        assertEquals(0 to 0, cellAt(x = 10f, y = 10f, sidePx = side, cellsPerSide = 4))
    }

    @Test
    fun `center maps to middle cell`() {
        assertEquals(1 to 1, cellAt(x = 150f, y = 150f, sidePx = side, cellsPerSide = 4))
    }

    @Test
    fun `bottom right maps to last row and col`() {
        assertEquals(3 to 3, cellAt(x = 399f, y = 399f, sidePx = side, cellsPerSide = 4))
    }

    @Test
    fun `each of n cells is hit across one axis`() {
        val n = 5
        val hits = (0 until n).map { i ->
            cellAt(x = side * (i + 0.5f) / n, y = side / 2, sidePx = side, cellsPerSide = n).second
        }
        assertEquals((0 until n).toList(), hits)
    }

    @Test
    fun `positions past the edge clamp to the boundary cells`() {
        assertEquals(3 to 0, cellAt(x = 50f, y = 900f, sidePx = side, cellsPerSide = 4))
        assertEquals(0 to 0, cellAt(x = -20f, y = 50f, sidePx = side, cellsPerSide = 4))
        assertEquals(3 to 3, cellAt(x = 500f, y = 500f, sidePx = side, cellsPerSide = 4))
    }

    @Test
    fun `single cell board maps everything to 0 0`() {
        assertEquals(0 to 0, cellAt(x = 0f, y = 0f, sidePx = 100f, cellsPerSide = 1))
        assertEquals(0 to 0, cellAt(x = 99f, y = 99f, sidePx = 100f, cellsPerSide = 1))
    }
}
