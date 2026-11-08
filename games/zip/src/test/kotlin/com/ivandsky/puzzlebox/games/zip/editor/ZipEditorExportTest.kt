package com.ivandsky.puzzlebox.games.zip.editor

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.Wall
import com.ivandsky.puzzlebox.games.zip.WallDirection
import com.ivandsky.puzzlebox.games.zip.ZipBoard
import com.ivandsky.puzzlebox.games.zip.codec.ZipCodec
import com.ivandsky.puzzlebox.games.zip.engine.ZipVerifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A Zip draft with a unique path exports through the daily [ZipCodec] with the path embedded. */
class ZipEditorExportTest {

    /** 5x5 snake, marks at the ends and the middle of row 2. */
    private val snake = listOf(0, 1, 2, 3, 4, 9, 8, 7, 6, 5, 10, 11, 12, 13, 14, 19, 18, 17, 16, 15, 20, 21, 22, 23, 24)
    private val draft = ZipDraft(
        n = 5,
        walls = setOf(Wall(Cell(0), WallDirection.DOWN), Wall(Cell(1), WallDirection.DOWN)),
        path = snake,
        waypoints = listOf(0, 12, 24),
    )

    private fun decoded(): com.ivandsky.puzzlebox.games.zip.ZipPuzzle {
        val puzzle = ZipEditor.toPuzzle(draft, Solution(snake))
        val result = ZipCodec.decode(puzzle.payload)
        return (result as DecodeResult.Ok).value
    }

    @Test
    fun `the exported payload decodes to the same board and solution`() {
        val puzzle = decoded()

        assertEquals(ZipBoard(5, draft.walls, draft.waypoints.map(::Cell)), puzzle.board)
        assertEquals(snake.map(::Cell), puzzle.solution)
        assertEquals(VariantId("zip/n5"), puzzle.variant)
    }

    @Test
    fun `the embedded solution passes the verifier`() {
        val puzzle = decoded()

        assertTrue(ZipVerifier.verifyPath(puzzle, puzzle.solution!!))
    }

    @Test
    fun `the puzzle carries the zip identity and codec version`() {
        val puzzle = ZipEditor.toPuzzle(draft, Solution(snake))

        assertEquals(ZipEditor.gameId, puzzle.gameId)
        assertEquals(ZipEditor.variantOf(draft), puzzle.variant)
        assertEquals(ZipCodec.VERSION, puzzle.formatVersion)
    }

    @Test
    fun `a zip draft can test solve`() {
        val editorDraft = EditorDraft(ZipEditor, DraftState(draft))

        assertTrue(editorDraft.canTestSolve)
        assertNotNull(editorDraft.toPuzzle(Solution(snake)))
    }
}
