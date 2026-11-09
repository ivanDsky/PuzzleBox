package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ThemeMode
import kotlinx.coroutines.flow.Flow

/**
 * User-level app preferences. Interfaces live in core; features see this only.
 * Backed by Preferences DataStore (small key-value state; Room stays for puzzle data
 * and a settings table would force a schema-v2 migration that is deferred).
 */
interface UserPreferencesRepository {
    val themeMode: Flow<ThemeMode>

    suspend fun setThemeMode(mode: ThemeMode)

    /** True once the player finished or skipped [game]'s tutorial; false before that. */
    fun tutorialSeen(game: GameId): Flow<Boolean>

    suspend fun markTutorialSeen(game: GameId)

    /** Whether the Studio opens each 2+ solutions verdict with its witness on the board; true until switched off. */
    val autoShowWitness: Flow<Boolean>

    suspend fun setAutoShowWitness(on: Boolean)

    /** Whether Sudoku tints the selected cell's row, column and box; true until switched off. */
    val sudokuPeerHighlight: Flow<Boolean>

    suspend fun setSudokuPeerHighlight(enabled: Boolean)

    /** Whether Sudoku highlights every cell holding the selected digit; true until switched off. */
    val sudokuSameDigitHighlight: Flow<Boolean>

    suspend fun setSudokuSameDigitHighlight(enabled: Boolean)
}
