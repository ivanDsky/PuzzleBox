package com.ivandsky.puzzlebox.games.sudokuui

import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.model.GameSetting

/** Sudoku's persisted settings. Both Sudoku games share them, stored under Mini (the shipped one). */
object SudokuSettings {
    val PeerHighlight = GameSetting(SudokuGames.MINI.id, "peer_highlight", default = true)
    val SameDigitHighlight = GameSetting(SudokuGames.MINI.id, "same_digit_highlight", default = true)
}
