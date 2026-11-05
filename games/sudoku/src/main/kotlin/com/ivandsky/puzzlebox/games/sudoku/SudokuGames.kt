package com.ivandsky.puzzlebox.games.sudoku

import com.ivandsky.puzzlebox.model.GameId

/** The two games the engine serves: LinkedIn's daily Mini Sudoku and the bundled classic. */
object SudokuGames {

    val MINI: SudokuDefinition = SudokuDefinition(GameId("minisudoku"), "Mini Sudoku", SudokuVariants.MINI)

    val CLASSIC: SudokuDefinition = SudokuDefinition(GameId("sudoku"), "Sudoku", SudokuVariants.CLASSIC)
}
