package com.ivandsky.puzzlebox.games.sudoku

import com.ivandsky.puzzlebox.game.VariantId

/** The variant table. A new board size is a new row here plus a source (spec A19). */
object SudokuVariants {

    val MINI: SudokuVariant = SudokuVariant(VariantId("sudoku/6x6"), SudokuGeometry(size = 6, boxRows = 2, boxCols = 3))

    val CLASSIC: SudokuVariant = SudokuVariant(VariantId("sudoku/9x9"), SudokuGeometry(size = 9, boxRows = 3, boxCols = 3))

    val all: List<SudokuVariant> = listOf(MINI, CLASSIC)

    /** Null for an id this build does not ship; callers never guess rules for it (ADR-0005). */
    fun byId(id: VariantId): SudokuVariant? = all.firstOrNull { it.id == id }

    fun bySize(size: Int): SudokuVariant? = all.firstOrNull { it.geometry.size == size }
}
