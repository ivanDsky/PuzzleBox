package com.ivandsky.puzzlebox.games.sudokuui

/**
 * One refused input: [message] is the caption, [seq] numbers the board's refusals so a repeat of
 * the same caption is still a new value the haptic can see.
 */
data class SudokuRejection(val message: String, val seq: Int)
