package com.ivandsky.puzzlebox.games.zipui.editor

import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.zip.editor.ZipDraft
import com.ivandsky.puzzlebox.gameui.editor.EditorOverlay
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * With two solutions the board draws one path: the pipe follows the solution the card describes
 * instead of a second line over the author's pipe, and only while that solution still belongs
 * to the path on the board.
 */
class ZipEditorSolutionPathTest {

    private val showB = EditorOverlay(EditorOverlay.Witness.B, showOnBoard = true)

    @Test
    fun `the pipe follows the selected solution`() {
        val diagnostics = zipDiagnostics(MULTIPLE, MULTIPLE, showB, drawn = A.values)

        assertEquals(B.values, diagnostics.witness)
        assertEquals(B.values, rendererModel(DRAWN, diagnostics.witness).path)
    }

    @Test
    fun `without a witness the pipe is the drawn path`() {
        val diagnostics = zipDiagnostics(MULTIPLE, MULTIPLE, EditorOverlay(showOnBoard = false), drawn = A.values)

        assertEquals(emptyList<Int>(), diagnostics.witness)
        assertEquals(A.values, rendererModel(DRAWN, diagnostics.witness).path)
    }

    @Test
    fun `a solution that no longer matches the drawn path is not drawn`() {
        val edited = listOf(0, 1, 2, 3)

        assertEquals(emptyList<Int>(), zipDiagnostics(MULTIPLE, MULTIPLE, showB, drawn = edited).witness)
    }

    @Test
    fun `a board without a path takes the solution as its only path`() {
        assertEquals(B.values, zipDiagnostics(MULTIPLE, MULTIPLE, showB, drawn = emptyList()).witness)
    }

    @Test
    fun `the differing cells stay the same whichever solution is selected`() {
        val a = zipDiagnostics(MULTIPLE, MULTIPLE, EditorOverlay(EditorOverlay.Witness.A, showOnBoard = true), A.values)
        val b = zipDiagnostics(MULTIPLE, MULTIPLE, showB, A.values)

        assertEquals(MULTIPLE.differingCells, a.diff)
        assertEquals(MULTIPLE.differingCells, b.diff)
    }

    private companion object {
        val A = Solution(listOf(0, 1, 2, 3, 4))
        val B = Solution(listOf(0, 5, 6, 7, 8))
        val MULTIPLE = Verdict.Multiple(A, B, setOf(1, 2, 3, 4, 5, 6, 7, 8))
        val DRAWN = ZipDraft(5, emptySet(), A.values, listOf(0, 4))
    }
}
