package com.ivandsky.puzzlebox.profile.settings

import kotlinx.coroutines.flow.Flow

/**
 * The in-game switches Settings mirrors, as Settings sees them. The app binds this to the same stored values the
 * games' own overflow toggles use, so a flip in either place shows in the other; Settings never names a game module.
 */
interface GameOptions {
    val queensAutoCross: Flow<Boolean>
    val sudokuPeerHighlight: Flow<Boolean>
    val sudokuSameDigitHighlight: Flow<Boolean>

    suspend fun setQueensAutoCross(enabled: Boolean)

    suspend fun setSudokuPeerHighlight(enabled: Boolean)

    suspend fun setSudokuSameDigitHighlight(enabled: Boolean)
}
