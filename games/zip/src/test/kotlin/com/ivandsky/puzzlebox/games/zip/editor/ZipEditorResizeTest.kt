package com.ivandsky.puzzlebox.games.zip.editor

import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.Wall
import com.ivandsky.puzzlebox.games.zip.WallDirection
import org.junit.Assert.assertEquals
import org.junit.Test

/** Resizing a Zip board keeps the top-left (A5): what fits stays, the ends are re-marked. */
class ZipEditorResizeTest {

    private val n5 = VariantId("zip/n5")

    private fun wall(cell: Int, dir: WallDirection) = Wall(Cell(cell), dir)

    private fun resize(draft: ZipDraft): ZipDraft =
        EditorDraft(ZipEditor, DraftState(draft)).resize(n5).state.draft

    /** 8x8 path: row 0 cells 0..7 then down column 7 to cell 15; marks at 0, 3, 15. */
    private fun big(path: List<Int>, waypoints: List<Int>, walls: Set<Wall> = emptySet()) =
        ZipDraft(8, walls, path, waypoints)

    @Test
    fun `the path is cut at its first cell outside and the ends are re-marked`() {
        val draft = big((0..7).toList() + 15, listOf(0, 3, 15))

        val resized = resize(draft)

        assertEquals(5, resized.n)
        assertEquals(listOf(0, 1, 2, 3, 4), resized.path)
        assertEquals(listOf(0, 3, 4), resized.waypoints)
    }

    @Test
    fun `cells keep their row and column under the new width`() {
        val draft = big(listOf(0, 8, 16, 17), listOf(0, 8, 17))

        val resized = resize(draft)

        assertEquals(listOf(0, 5, 10, 11), resized.path)
        assertEquals(listOf(0, 5, 11), resized.waypoints)
    }

    @Test
    fun `walls and waypoints outside are dropped and walls inside are kept`() {
        val walls = setOf(wall(9, WallDirection.RIGHT), wall(20, WallDirection.DOWN), wall(4, WallDirection.RIGHT), wall(32, WallDirection.DOWN))
        val draft = big(listOf(0, 1, 2), listOf(0, 1, 2), walls)

        val resized = resize(draft)

        assertEquals(setOf(wall(6, WallDirection.RIGHT), wall(14, WallDirection.DOWN)), resized.walls)
    }

    @Test
    fun `a path that starts outside leaves an empty path`() {
        val draft = big(listOf(7, 6, 5), listOf(7, 6, 5))

        val resized = resize(draft)

        assertEquals(emptyList<Int>(), resized.path)
        assertEquals(emptyList<Int>(), resized.waypoints)
    }

    @Test
    fun `a board with no path keeps the waypoints that fit`() {
        val draft = big(emptyList(), listOf(0, 7, 9))

        assertEquals(listOf(0, 6), resize(draft).waypoints)
    }

    @Test
    fun `a path that fits is kept whole`() {
        val draft = big(listOf(0, 1, 2), listOf(0, 1, 2))

        assertEquals(draft.path, resize(draft).path)
        assertEquals(draft.waypoints, resize(draft).waypoints)
    }

    @Test
    fun `growing keeps the board in the top-left`() {
        val draft = ZipDraft(5, setOf(wall(6, WallDirection.DOWN)), listOf(0, 1, 6), listOf(0, 6))

        val resized = EditorDraft(ZipEditor, DraftState(draft)).resize(VariantId("zip/n8")).state.draft

        assertEquals(8, resized.n)
        assertEquals(listOf(0, 1, 9), resized.path)
        assertEquals(setOf(wall(9, WallDirection.DOWN)), resized.walls)
    }
}
