package com.ivandsky.puzzlebox.games.sudoku.editor

import com.ivandsky.puzzlebox.game.editor.PublishBlock
import com.ivandsky.puzzlebox.game.editor.Verdict

/**
 * Sudoku board gates outside the solver (spec A8). A Unique board the hint ladder cannot
 * finish ([Verdict.Unique.needsGuessing], spec A9) carries one [PublishBlock.NEEDS_GUESSING].
 */
object SudokuPublishGates {
    /** Every PublishBlock id [blocks] can emit, so a copy table can prove it covers them all. */
    val blockIds: Set<String> = setOf(PublishBlock.NEEDS_GUESSING)

    fun blocks(draft: SudokuDraft, verdict: Verdict): List<PublishBlock> =
        if (verdict is Verdict.Unique && verdict.needsGuessing) {
            listOf(PublishBlock(PublishBlock.NEEDS_GUESSING))
        } else {
            emptyList()
        }
}
