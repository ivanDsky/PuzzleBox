package com.ivandsky.puzzlebox.games.sudoku

import com.ivandsky.puzzlebox.game.VariantId

/** One shipped board shape, pinned by [id] in every stored puzzle (ADR-0005). */
data class SudokuVariant(val id: VariantId, val geometry: SudokuGeometry)
