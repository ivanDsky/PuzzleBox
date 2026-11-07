package com.ivandsky.puzzlebox.games.sudokuui

import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.gameui.GameBrand

/** Classic Sudoku's brand: no hue of its own, and the same [GameGlyphs.Sudoku] as Mini, registered in [sudokuUiModule]. */
internal val ClassicSudokuBrand = GameBrand(AccentSlot.Neutral, GameGlyphs.Sudoku)
