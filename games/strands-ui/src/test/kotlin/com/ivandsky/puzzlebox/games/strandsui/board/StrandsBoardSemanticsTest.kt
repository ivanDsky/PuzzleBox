package com.ivandsky.puzzlebox.games.strandsui.board

import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.render.CellState
import com.ivandsky.puzzlebox.games.strands.render.StrandsCell
import org.junit.Assert.assertEquals
import org.junit.Test

/** SC12 letter labels. */
class StrandsBoardSemanticsTest {

    private fun cell(state: CellState, traceIndex: Int? = null) = StrandsCell(Coord(0, 0), 'S', state, traceIndex)

    @Test
    fun `a letter reads its letter, then selected, then its state`() {
        assertEquals("S", StrandsBoardSemantics.label(cell(CellState.Idle)))
        assertEquals("S, selected", StrandsBoardSemantics.label(cell(CellState.Idle, traceIndex = 0)))
        assertEquals("S, theme", StrandsBoardSemantics.label(cell(CellState.FoundTheme)))
        assertEquals("S, spangram", StrandsBoardSemantics.label(cell(CellState.FoundSpangram)))
        assertEquals("S, hinted", StrandsBoardSemantics.label(cell(CellState.HintOutlined)))
        assertEquals("S, selected, hinted", StrandsBoardSemantics.label(cell(CellState.HintOrderRevealed(2), traceIndex = 1)))
    }
}
