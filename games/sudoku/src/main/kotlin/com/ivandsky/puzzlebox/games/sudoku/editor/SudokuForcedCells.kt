package com.ivandsky.puzzlebox.games.sudoku.editor

import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.SudokuVariants
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuHints
import com.ivandsky.puzzlebox.games.sudoku.engine.rules.SudokuConflicts

/** Pure, read-only aid: the cells logic alone places from a draft's givens. */
object SudokuForcedCells {
    private val hints = SudokuHints()

    /**
     * cell → digit for every empty cell the SudokuHints ladder places from [draft]'s givens,
     * walked until it would need Reveal or the grid is full (Reveal never counts). Empty when
     * the givens clash.
     */
    fun of(draft: SudokuDraft): Map<Int, Int> {
        val geometry = requireNotNull(SudokuVariants.bySize(draft.size)).geometry
        val clash = SudokuBoard(
            geometry,
            draft.givens.map { if (it == 0) SudokuCell.Empty else SudokuCell.Given(it) },
        )
        if (SudokuConflicts.find(clash).isNotEmpty()) return emptyMap()

        val known = draft.givens.toIntArray()
        val forced = linkedMapOf<Int, Int>()
        while (true) {
            val place = hints.placement(geometry, known) ?: return forced
            known[place.cell] = place.digit
            forced[place.cell] = place.digit
        }
    }
}
