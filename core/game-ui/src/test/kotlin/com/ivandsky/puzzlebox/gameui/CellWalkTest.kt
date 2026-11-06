package com.ivandsky.puzzlebox.gameui

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class CellWalkTest {

    private val geometry = GridGeometry(rows = 5, cols = 5, widthPx = 500f, heightPx = 500f)

    private fun enters(vararg cells: Pair<Int, Int>) = cells.map { WalkStep.Enter(GridCell(it.first, it.second)) }

    @Test
    fun `a horizontal segment crosses every cell between its ends`() {
        assertEquals(enters(0 to 1, 0 to 2, 0 to 3), geometry.walkCells(Offset(50f, 50f), Offset(350f, 50f)))
    }

    @Test
    fun `a slanted segment visits 4-connected cells in crossing order`() {
        // (0.5,0.5)→(2.5,1.5) in cell units crosses x=1, then y=1, then x=2.
        assertEquals(enters(0 to 1, 1 to 1, 1 to 2), geometry.walkCells(Offset(50f, 50f), Offset(250f, 150f)))
    }

    @Test
    fun `an exact vertex crossing reports the favoured side, the other side, then the diagonal`() {
        assertEquals(
            listOf(WalkStep.Corner(GridCell(1, 0), GridCell(0, 1), GridCell(1, 1))),
            geometry.walkCells(Offset(50f, 0f), Offset(150f, 200f)).take(1),
        )
    }

    @Test
    fun `a segment inside one cell crosses nothing`() {
        assertEquals(emptyList<WalkStep>(), geometry.walkCells(Offset(10f, 10f), Offset(90f, 90f)))
    }

    @Test
    fun `an off-board end clamps to the boundary cell`() {
        assertEquals(enters(4 to 4), geometry.walkCells(Offset(350f, 450f), Offset(900f, 450f)))
    }
}
