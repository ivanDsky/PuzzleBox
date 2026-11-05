package com.ivandsky.puzzlebox.games.strands.render

import com.ivandsky.puzzlebox.games.strands.ActiveHint
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.HintStage
import com.ivandsky.puzzlebox.games.strands.StrandsState
import com.ivandsky.puzzlebox.games.strands.engine.StrandsMove
import com.ivandsky.puzzlebox.games.strands.engine.StrandsRules
import com.ivandsky.puzzlebox.games.strands.engine.StrandsVerdict
import com.ivandsky.puzzlebox.games.strands.engine.accept
import com.ivandsky.puzzlebox.games.strands.tinyPuzzle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrandsRendererModelTest {

    private val fresh = StrandsState(tinyPuzzle)
    private val catCells = tinyPuzzle.themeWords.single().cells
    private val spangramCells = tinyPuzzle.spangram.cells

    private fun StrandsRendererModel.cell(row: Int, col: Int) = cells[row * cols + col]

    @Test
    fun `a fresh board is idle, row-major, with the counts`() {
        val model = StrandsRendererModel.of(fresh)
        assertEquals("CATATS", model.cells.map { it.letter }.joinToString(""))
        assertEquals((0 until 2).flatMap { r -> (0 until 3).map { Coord(r, it) } }, model.cells.map { it.coord })
        assertTrue(model.cells.all { it.state == CellState.Idle && it.traceIndex == null })
        assertEquals(emptyList<StrandsLink>(), model.links)
        assertEquals(0, model.foundCount)
        assertEquals(2, model.totalCount)
        assertFalse(model.complete)
    }

    @Test
    fun `a spelled theme word paints its intended cells, not the traced path`() {
        val tracedElsewhere = StrandsMove(listOf(Coord(0, 0), Coord(1, 0), Coord(1, 1)))
        val verdict = StrandsRules.validate(tracedElsewhere, fresh) as StrandsVerdict
        val model = StrandsRendererModel.of(fresh.accept(verdict))

        catCells.forEach { assertEquals(CellState.FoundTheme, model.cell(it.row, it.col).state) }
        assertEquals(CellState.Idle, model.cell(1, 0).state)
        assertEquals(CellState.Idle, model.cell(1, 1).state)
        assertEquals(listOf(StrandsLink(catCells, LinkKind.Theme)), model.links)
        assertEquals(1, model.foundCount)
    }

    @Test
    fun `found beats hint, spangram beats theme`() {
        val outlined = StrandsRendererModel.of(fresh.copy(activeHint = ActiveHint("CAT", HintStage.Outlined)))
        catCells.forEach { assertEquals(CellState.HintOutlined, outlined.cell(it.row, it.col).state) }

        val revealed = StrandsRendererModel.of(fresh.copy(activeHint = ActiveHint("CAT", HintStage.OrderRevealed)))
        catCells.forEachIndexed { i, c -> assertEquals(CellState.HintOrderRevealed(i), revealed.cell(c.row, c.col).state) }

        // A stale hint on a found word (not producible by accept) still loses to the found state.
        val foundAndHinted = fresh.copy(found = listOf("CAT"), activeHint = ActiveHint("CAT", HintStage.OrderRevealed))
        catCells.forEach {
            assertEquals(CellState.FoundTheme, StrandsRendererModel.of(foundAndHinted).cell(it.row, it.col).state)
        }

        val all = StrandsRendererModel.of(fresh.copy(found = listOf("ATS", "CAT")))
        spangramCells.forEach { assertEquals(CellState.FoundSpangram, all.cell(it.row, it.col).state) }
        catCells.forEach { assertEquals(CellState.FoundTheme, all.cell(it.row, it.col).state) }
        assertTrue(all.complete)
        assertEquals(2, all.foundCount)
    }

    @Test
    fun `a traced hinted cell keeps HintOutlined with a traceIndex`() {
        val hinted = fresh.copy(activeHint = ActiveHint("CAT", HintStage.Outlined))
        val trace = listOf(Coord(1, 0), Coord(0, 0))
        val model = StrandsRendererModel.of(hinted, trace)

        assertEquals(StrandsCell(Coord(0, 0), 'C', CellState.HintOutlined, traceIndex = 1), model.cell(0, 0))
        assertEquals(StrandsCell(Coord(1, 0), 'A', CellState.Idle, traceIndex = 0), model.cell(1, 0))
        assertEquals(null, model.cell(0, 1).traceIndex)
    }

    @Test
    fun `links run found words in find order, then the revealed hint, then the trace`() {
        val state = fresh.copy(found = listOf("ATS"), activeHint = ActiveHint("CAT", HintStage.OrderRevealed))
        val trace = listOf(Coord(0, 2), Coord(0, 1))
        assertEquals(
            listOf(
                StrandsLink(spangramCells, LinkKind.Spangram),
                StrandsLink(catCells, LinkKind.Hint),
                StrandsLink(trace, LinkKind.Trace),
            ),
            StrandsRendererModel.of(state, trace).links,
        )
        val outlined = state.copy(activeHint = ActiveHint("CAT", HintStage.Outlined))
        assertEquals(listOf(StrandsLink(spangramCells, LinkKind.Spangram)), StrandsRendererModel.of(outlined).links)
    }
}
