package com.ivandsky.puzzlebox.games.sudoku

/**
 * Published boards with their known solutions, shared by the engine tests. Grids are
 * row-major strings: a digit per cell, '.' or '0' for an open cell, whitespace ignored.
 */
object KnownSolutions {

    /** LinkedIn Mini Sudoku #450 "Peanut" (research 01, fetched 2026-11-04 PT). */
    const val PEANUT_450_GIVENS = "..3.5. .2.4.6 1..... .....2 2.4.1. .1.3.."
    const val PEANUT_450_SOLUTION = "463251 521436 142563 356142 234615 615324"
    val PEANUT_450_PRESETS: List<Int> = listOf(2, 4, 7, 9, 11, 12, 23, 24, 26, 28, 31, 33)

    /** A 6x6 board with a unique solution (research 04). */
    const val MINI_SAMPLE_GIVENS = "....62..643..2.5.3..1....3...4.65.2."
    const val MINI_SAMPLE_SOLUTION = "543162216435624513351246132654465321"

    /** The classic 9x9 textbook board (research 04). */
    const val CLASSIC_SAMPLE_GIVENS =
        "53..7....6..195....98....6.8...6...34..8.3..17...2...6.6....28....419..5....8..79"
    const val CLASSIC_SAMPLE_SOLUTION =
        "534678912672195348198342567859761423426853791713924856961537284287419635345286179"

    val peanut450: SudokuPuzzle = SudokuPuzzle(SudokuVariants.MINI, grid(PEANUT_450_GIVENS), grid(PEANUT_450_SOLUTION))
    val miniSample: SudokuPuzzle = SudokuPuzzle(SudokuVariants.MINI, grid(MINI_SAMPLE_GIVENS), grid(MINI_SAMPLE_SOLUTION))
    val classicSample: SudokuPuzzle =
        SudokuPuzzle(SudokuVariants.CLASSIC, grid(CLASSIC_SAMPLE_GIVENS), grid(CLASSIC_SAMPLE_SOLUTION))

    fun grid(text: String): List<Int> = text.filterNot { it.isWhitespace() }.map { if (it == '.') 0 else it.digitToInt() }

    /** A test resource under /fixtures as text. */
    fun fixture(name: String): String =
        requireNotNull(KnownSolutions::class.java.getResourceAsStream("/fixtures/$name")) { "missing fixture $name" }
            .bufferedReader().use { it.readText() }
}
