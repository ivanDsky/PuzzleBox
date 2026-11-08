package com.ivandsky.puzzlebox.games.sudokuui.editor

import com.ivandsky.puzzlebox.game.editor.PublishBlock
import com.ivandsky.puzzlebox.games.sudoku.editor.SudokuDraftAnalyzer

/**
 * The editor's fix-list words: one sentence per reason and block id. A new id adds one entry
 * to [fixes]; SudokuCopyCoverageTest fails while it is missing.
 */
internal object SudokuEditorCopy {

    private val fixes: Map<String, String> = mapOf(
        SudokuDraftAnalyzer.EMPTY to "Add a given to start",
        PublishBlock.NEEDS_GUESSING to "Logic alone can't finish it — players would have to guess",
    )

    /** The fix-list sentence for a reason or block [id]; null when it has no copy. */
    fun fix(id: String): String? = fixes[id]
}
