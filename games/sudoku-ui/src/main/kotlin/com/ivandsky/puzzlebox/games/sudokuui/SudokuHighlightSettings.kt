package com.ivandsky.puzzlebox.games.sudokuui

import kotlinx.coroutines.flow.Flow

/**
 * The player's two board tints, as the Sudoku controller sees them: the row/column/box tint
 * of the selected cell ([peer]) and the tint on every cell holding its digit ([sameDigit]).
 * Both default on in the app's binding. The app binds this to the persisted preferences; the module never reads storage.
 */
interface SudokuHighlightSettings {
    val peer: Flow<Boolean>
    val sameDigit: Flow<Boolean>

    suspend fun setPeer(enabled: Boolean)

    suspend fun setSameDigit(enabled: Boolean)
}
