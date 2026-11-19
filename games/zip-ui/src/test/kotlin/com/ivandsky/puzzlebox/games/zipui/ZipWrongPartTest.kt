package com.ivandsky.puzzlebox.games.zipui

import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.games.zip.Cell
import org.junit.Assert.assertEquals
import org.junit.Test

/** Where the wrong part of a Zip line starts (backlog #1585), on the two boards of the ticket. */
class ZipWrongPartTest {

    private fun cells(idx: List<Int>) = idx.map(::Cell)

    @Test
    fun `the wrong part of a detour starts at the last cell that agrees with the solution`() {
        // Bug it catches: a cut allowed one cell short of, or past, the last right cell.
        val wrongPart = ZipWrongPart(ZipCutWrongPartFixture.daily527())
        assertEquals(ZipCutWrongPartFixture.LAST_RIGHT_527, wrongPart.cutFrom(cells(ZipCutWrongPartFixture.detour527)))
    }

    @Test
    fun `a line that is right so far has no wrong part`() {
        // Bug it catches: the correct part of a line cut by a slide back, losing the player's right cells.
        val wrongPart = ZipWrongPart(ZipCutWrongPartFixture.phone3())
        val line = cells(ZipCutWrongPartFixture.right3)
        assertEquals(line.size, wrongPart.cutFrom(line))
    }

    @Test
    fun `a board with no stored solution finds the same start by search`() {
        // Bug it catches: the wrong part known only for boards that carry their solution.
        val wrongPart = ZipWrongPart(ZipCutWrongPartFixture.daily527(stored = false))
        assertEquals(ZipCutWrongPartFixture.LAST_RIGHT_527, wrongPart.cutFrom(cells(ZipCutWrongPartFixture.detour527)))
    }

    @Test
    fun `a won line with no stored solution has no wrong part and asks for no cell after it`() {
        // Bug it catches: a slide over a won board with no stored solution crashing the hint's step past the last cell.
        val wrongPart = ZipWrongPart(ZipCutWrongPartFixture.daily527(stored = false))
        val won = cells(ZipCutWrongPartFixture.solution527Cells)
        assertEquals(won.size, wrongPart.cutFrom(won))
    }

    @Test
    fun `the answer follows the line it is asked about`() {
        // Bug it catches: the kept answer of the last line served to a line that has moved on.
        val wrongPart = ZipWrongPart(ZipCutWrongPartFixture.daily527())
        val detour = cells(ZipCutWrongPartFixture.detour527)
        assertEquals(ZipCutWrongPartFixture.LAST_RIGHT_527, wrongPart.cutFrom(detour))
        assertEquals(40, wrongPart.cutFrom(detour.take(40)))
        assertEquals(ZipCutWrongPartFixture.LAST_RIGHT_527, wrongPart.cutFrom(detour))
    }

    @Test
    fun `the hooks pass the wrong part on in grid cells, and without it name none`() {
        // Bug it catches: the cell mapping off by a row/column swap, or the flag-off hooks cutting anyway.
        val puzzle = ZipCutWrongPartFixture.daily527()
        val line = ZipCutWrongPartFixture.detour527.map { GridCell(it / 7, it % 7) }
        assertEquals(ZipCutWrongPartFixture.LAST_RIGHT_527, ZipDragHooks(puzzle.board, ZipWrongPart(puzzle)).cutFrom(line))
        assertEquals(line.size, ZipDragHooks(puzzle.board).cutFrom(line))
    }
}
