package com.ivandsky.puzzlebox.games.zipui

import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.Wall
import com.ivandsky.puzzlebox.games.zip.WallDirection
import com.ivandsky.puzzlebox.games.zip.ZipBoard
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which step out of the tail cuts a free cell off (ticket 1572): a free cell beside the tail
 * needs a way in and a way out once the step is taken, the last number only a way in.
 * Cells are (column, row), as in the ticket.
 */
class ZipDragHooksTest {

    private fun cell(col: Int, row: Int) = GridCell(row, col)

    private fun board(n: Int, vararg numbers: Pair<Int, Int>, walls: Set<Wall> = emptySet()) =
        ZipBoard(n = n, walls = walls, waypoints = numbers.map { (col, row) -> Cell(row * n + col) })

    @Test
    fun `stepping past an open corner strands it, turning into the corner strands nothing`() {
        // Zip #527 at 0:16.20, cut to its top-left: the line came up column 0 to (0,1); (0,0),
        // (1,0) and (1,1) are free. Into (1,1), the corner keeps only (1,0) as a way in or out.
        val hooks = ZipDragHooks(board(3, 0 to 2, 2 to 2))
        val path = listOf(cell(0, 2), cell(0, 1))
        assertTrue(hooks.strands(path, cell(1, 1)))
        assertFalse(hooks.strands(path, cell(0, 0)))
    }

    @Test
    fun `a free cell with one way in strands unless it is the last number`() {
        // Tail (1,1) from (0,0) down and across; east to (2,1) leaves (1,0) only (2,0).
        val path = listOf(cell(0, 0), cell(0, 1), cell(1, 1))
        assertTrue(ZipDragHooks(board(3, 0 to 0, 2 to 2)).strands(path, cell(2, 1)))
        assertFalse(ZipDragHooks(board(3, 0 to 0, 1 to 0)).strands(path, cell(2, 1)))
    }

    @Test
    fun `a wall is no way in or out`() {
        // As in the corner case, with (1,1) walled from (2,1) and from (1,0): stepping up to
        // (0,0) would leave (1,1) only (1,2).
        val walled = setOf(Wall(Cell(1 * 3 + 1), WallDirection.RIGHT), Wall(Cell(0 * 3 + 1), WallDirection.DOWN))
        val path = listOf(cell(0, 2), cell(0, 1))
        assertTrue(ZipDragHooks(board(3, 0 to 2, 2 to 2, walls = walled)).strands(path, cell(0, 0)))
        assertFalse(ZipDragHooks(board(3, 0 to 2, 2 to 2)).strands(path, cell(0, 0)))
    }
}
