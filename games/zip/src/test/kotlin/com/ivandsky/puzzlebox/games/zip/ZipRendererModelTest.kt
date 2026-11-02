package com.ivandsky.puzzlebox.games.zip

import com.ivandsky.puzzlebox.games.zip.engine.ZipHint
import com.ivandsky.puzzlebox.games.zip.engine.ZipState
import com.ivandsky.puzzlebox.games.zip.engine.ZipValidation
import com.ivandsky.puzzlebox.games.zip.render.ZipRendererModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Renderer model mapping: game state -> paint-by-numbers for game-ui's BoardGrid (F2 §7). */
class ZipRendererModelTest {

    private val puzzle = ZipTestSupport.n5Puzzle()

    @Test
    fun `cells map waypoints, labels, and the start marker`() {
        val model = ZipRendererModel.of(ZipState(puzzle, emptyList()), null)
        assertEquals(25, model.cells.size)
        assertEquals(
            ZipRendererModel.CellRender(numberLabel = 1, isWaypoint = true, isStart = true),
            model.cells[0],
        )
        assertEquals(
            ZipRendererModel.CellRender(numberLabel = 2, isWaypoint = true, isStart = false),
            model.cells[12],
        )
        assertEquals(
            ZipRendererModel.CellRender(numberLabel = 3, isWaypoint = true, isStart = false),
            model.cells[24],
        )
        assertEquals(
            ZipRendererModel.CellRender(numberLabel = null, isWaypoint = false, isStart = false),
            model.cells[1],
        )
    }

    @Test
    fun `walls map sorted with wire directions`() {
        val model = ZipRendererModel.of(ZipState(puzzle, emptyList()), null)
        assertEquals(16, model.walls.size)
        assertEquals(ZipRendererModel.WallRender(0, WallDirection.DOWN), model.walls.first())
        assertEquals(ZipRendererModel.WallRender(19, WallDirection.DOWN), model.walls.last())
        // sorted by (cellIdx, direction): all cell indices ascend
        assertEquals(model.walls.map { it.cellIdx }, model.walls.map { it.cellIdx }.sorted())
    }

    @Test
    fun `hint highlight carries the step cell only`() {
        val state = ZipState(puzzle, ZipTestSupport.path(0, 1, 2))
        val stepped = ZipRendererModel.of(state, ZipHint.Step(Cell(3)))
        assertEquals(3, stepped.hintHighlight)
        // F2 §7: the registered board draws the trace from the model alone — n + path flow through
        assertEquals(5, stepped.n)
        assertEquals(listOf(0, 1, 2), stepped.path)
        assertNull(ZipRendererModel.of(state, null).hintHighlight)
        // a backtrack hint highlights nothing (the trim is applied before re-hinting)
        assertNull(ZipRendererModel.of(state, ZipHint.Backtrack(1)).hintHighlight)
    }

    @Test
    fun `rejection carries the cell and reason through`() {
        val rejection = ZipRendererModel.RejectedMove(Cell(5).idx, ZipValidation.Reason.WallBetween)
        val model = ZipRendererModel.of(ZipState(puzzle, ZipTestSupport.path(0)), null, rejection)
        assertEquals(rejection, model.rejection)
        assertNull(ZipRendererModel.of(ZipState(puzzle, emptyList()), null).rejection)
    }

    @Test
    fun `status derives from the trace`() {
        assertEquals(ZipRendererModel.Status.IDLE, ZipRendererModel.of(ZipState(puzzle, emptyList()), null).status)
        assertEquals(
            ZipRendererModel.Status.PLAYING,
            ZipRendererModel.of(ZipState(puzzle, ZipTestSupport.path(0, 1)), null).status,
        )
        assertEquals(
            ZipRendererModel.Status.COMPLETE,
            ZipRendererModel.of(ZipState(puzzle, ZipTestSupport.n5Solution), null).status,
        )
    }
}
