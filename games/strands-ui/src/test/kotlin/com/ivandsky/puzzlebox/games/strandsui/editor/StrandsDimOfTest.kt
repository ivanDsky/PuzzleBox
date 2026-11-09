package com.ivandsky.puzzlebox.games.strandsui.editor

import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraft
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraftPath
import com.ivandsky.puzzlebox.games.strands.render.LinkKind
import com.ivandsky.puzzlebox.games.strands.render.StrandsLink
import com.ivandsky.puzzlebox.games.strandsui.board.StrandsDim
import org.junit.Assert.assertEquals
import org.junit.Test

/** What [dimOf] fades: every connector but the selected path's, and every cell off it. */
class StrandsDimOfTest {

    private val board = StrandsDraft(
        letters = StrandsDraft.BLANK.toString().repeat(StrandsDraft.ROWS * StrandsDraft.COLS),
        clue = "",
        paths = listOf(
            StrandsDraftPath(listOf(0, 1, 2), null),
            StrandsDraftPath(listOf(6, 7, 8), null),
            StrandsDraftPath(listOf(12, 13, 14), null),
        ),
        spangram = 2,
    )

    private fun link(index: Int, kind: LinkKind) = StrandsLink(board.paths[index].cells.map(::coordOf), kind)

    @Test
    fun `every connector but the selected path's is dimmed, the spangram's included`() {
        val dim = dimOf(board, selected = 1)
        assertEquals(setOf(link(0, LinkKind.Theme), link(2, LinkKind.Spangram)), dim.links)
    }

    @Test
    fun `selecting the spangram dims every theme connector and keeps its own`() {
        val dim = dimOf(board, selected = 2)
        assertEquals(setOf(link(0, LinkKind.Theme), link(1, LinkKind.Theme)), dim.links)
    }

    @Test
    fun `every cell off the selected path is dimmed, the path's cells are kept`() {
        val dim = dimOf(board, selected = 1)
        assertEquals(StrandsDraft.ROWS * StrandsDraft.COLS - 3, dim.cells.size)
        assertEquals(emptySet<Any>(), dim.cells intersect board.paths[1].cells.map(::coordOf).toSet())
    }

    @Test
    fun `nothing selected, or a stale index, dims nothing`() {
        assertEquals(StrandsDim.None, dimOf(board, selected = null))
        assertEquals(StrandsDim.None, dimOf(board, selected = 3))
        assertEquals(StrandsDim.None, dimOf(board, selected = -1))
    }
}
