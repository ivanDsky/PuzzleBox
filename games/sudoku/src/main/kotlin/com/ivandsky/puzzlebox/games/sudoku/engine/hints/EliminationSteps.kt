package com.ivandsky.puzzlebox.games.sudoku.engine.hints

/** The elimination ladder [SudokuHints] climbs when no single applies, easiest first. */
internal object EliminationSteps {
    val DEFAULT: List<EliminationStep> = listOf(
        IntersectionSteps.POINTING,
        IntersectionSteps.CLAIMING,
        SubsetSteps.NAKED_PAIR,
        SubsetSteps.HIDDEN_PAIR,
        SubsetSteps.NAKED_TRIPLE,
        SubsetSteps.HIDDEN_TRIPLE,
    )
}
