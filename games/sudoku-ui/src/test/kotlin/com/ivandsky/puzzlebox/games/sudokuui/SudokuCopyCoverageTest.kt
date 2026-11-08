package com.ivandsky.puzzlebox.games.sudokuui

import com.ivandsky.puzzlebox.games.sudoku.editor.SudokuDraftAnalyzer
import com.ivandsky.puzzlebox.games.sudoku.editor.SudokuPublishGates
import com.ivandsky.puzzlebox.games.sudokuui.editor.SudokuEditorCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every id the Sudoku engine can hand the UI has fix-list copy. */
class SudokuCopyCoverageTest {

    @Test
    fun `every reason and block id has copy`() {
        val ids = SudokuDraftAnalyzer.reasonIds + SudokuPublishGates.blockIds
        assertTrue("the engine lists its ids", ids.isNotEmpty())

        val missing = ids.filter { SudokuEditorCopy.fix(it).isNullOrBlank() }

        assertEquals(emptyList<String>(), missing)
    }

    @Test
    fun `an unknown id has no copy`() {
        assertNull(SudokuEditorCopy.fix("sudoku.unknown"))
    }
}
