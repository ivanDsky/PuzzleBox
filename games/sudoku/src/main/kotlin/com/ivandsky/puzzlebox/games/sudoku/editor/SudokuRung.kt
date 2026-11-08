package com.ivandsky.puzzlebox.games.sudoku.editor

import com.ivandsky.puzzlebox.game.editor.Difficulty.Level
import com.ivandsky.puzzlebox.game.editor.LadderRung
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuTechnique

/**
 * The Sudoku hint ladder's [SudokuTechnique]s grouped into the families a difficulty label
 * names. Declared easiest first, so ordinal order is difficulty order.
 */
enum class SudokuRung(
    override val id: String,
    override val level: Level,
    override val captionKey: String,
) : LadderRung {
    SINGLES("singles", Level.Easy, "sudoku.rung.singles"),
    INTERSECTIONS("intersections", Level.Medium, "sudoku.rung.intersections"),
    PAIRS("pairs", Level.Hard, "sudoku.rung.pairs"),
    TRIPLES("triples", Level.Expert, "sudoku.rung.triples");

    companion object {
        fun of(technique: SudokuTechnique): SudokuRung = when (technique) {
            SudokuTechnique.HIDDEN_SINGLE_BOX,
            SudokuTechnique.HIDDEN_SINGLE_LINE,
            SudokuTechnique.NAKED_SINGLE -> SINGLES
            SudokuTechnique.POINTING,
            SudokuTechnique.CLAIMING -> INTERSECTIONS
            SudokuTechnique.NAKED_PAIR,
            SudokuTechnique.HIDDEN_PAIR -> PAIRS
            SudokuTechnique.NAKED_TRIPLE,
            SudokuTechnique.HIDDEN_TRIPLE -> TRIPLES
        }
    }
}
