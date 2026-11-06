package com.ivandsky.puzzlebox.games.sudoku

/**
 * The bundled 9x9 board: Wikimedia Commons "Sudoku-by-L2G-20050714", public domain
 * (research 04). Row-major, `.` = open cell.
 */
object SudokuSamples {

    const val CLASSIC_GIVENS: String =
        "53..7....6..195....98....6.8...6...34..8.3..17...2...6.6....28....419..5....8..79"

    const val CLASSIC_SOLUTION: String =
        "534678912672195348198342567859761423426853791713924856961537284287419635345286179"

    val classic: SudokuPuzzle = SudokuPuzzle(
        variant = SudokuGames.CLASSIC.variant,
        givens = CLASSIC_GIVENS.map { if (it == '.') 0 else it.digitToInt() },
        solution = CLASSIC_SOLUTION.map { it.digitToInt() },
    )
}
