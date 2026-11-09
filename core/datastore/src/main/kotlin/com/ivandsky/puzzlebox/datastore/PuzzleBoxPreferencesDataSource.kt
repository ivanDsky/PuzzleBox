package com.ivandsky.puzzlebox.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Everything the app keeps in its Preferences DataStore file. */
data class PuzzleBoxPreferences(
    val themeMode: ThemeMode,
    /** Games whose tutorial the player finished or skipped. */
    val tutorialsSeen: Set<GameId>,
    /** Whether the Studio opens each 2+ solutions verdict with its witness on the board; on until switched off. */
    val autoShowWitness: Boolean,
    /** Whether Sudoku tints the selected cell's row, column and box; on until switched off. */
    val sudokuPeerHighlight: Boolean,
    /** Whether Sudoku highlights every cell holding the selected digit; on until switched off. */
    val sudokuSameDigitHighlight: Boolean,
)

/**
 * Owns the Preferences DataStore keys. Reads tolerate a missing or corrupt value by
 * falling back to [ThemeMode.SYSTEM]; writes store the enum name so the file stays
 * human-readable and forward-compatible. Seen tutorials are one string set of game ids.
 * Auto-show and the two Sudoku highlights are plain booleans, true while their key is absent.
 */
class PuzzleBoxPreferencesDataSource(
    private val dataStore: DataStore<Preferences>,
) {

    val preferences: Flow<PuzzleBoxPreferences> = dataStore.data.map { preferences ->
        PuzzleBoxPreferences(
            themeMode = preferences[KEY_THEME_MODE]?.let(ThemeMode::fromStored) ?: ThemeMode.SYSTEM,
            tutorialsSeen = preferences[KEY_TUTORIALS_SEEN].orEmpty().mapTo(mutableSetOf(), ::GameId),
            autoShowWitness = preferences[KEY_AUTO_SHOW_WITNESS] ?: true,
            sudokuPeerHighlight = preferences[KEY_SUDOKU_PEER_HIGHLIGHT] ?: true,
            sudokuSameDigitHighlight = preferences[KEY_SUDOKU_SAME_DIGIT_HIGHLIGHT] ?: true,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { preferences -> preferences[KEY_THEME_MODE] = mode.name }
    }

    suspend fun markTutorialSeen(game: GameId) {
        dataStore.edit { preferences ->
            preferences[KEY_TUTORIALS_SEEN] = preferences[KEY_TUTORIALS_SEEN].orEmpty() + game.value
        }
    }

    suspend fun setAutoShowWitness(on: Boolean) {
        dataStore.edit { preferences -> preferences[KEY_AUTO_SHOW_WITNESS] = on }
    }

    suspend fun setSudokuPeerHighlight(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[KEY_SUDOKU_PEER_HIGHLIGHT] = enabled }
    }

    suspend fun setSudokuSameDigitHighlight(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[KEY_SUDOKU_SAME_DIGIT_HIGHLIGHT] = enabled }
    }

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_TUTORIALS_SEEN = stringSetPreferencesKey("tutorials_seen")
        val KEY_AUTO_SHOW_WITNESS = booleanPreferencesKey("auto_show_witness")
        val KEY_SUDOKU_PEER_HIGHLIGHT = booleanPreferencesKey("sudoku_peer_highlight")
        val KEY_SUDOKU_SAME_DIGIT_HIGHLIGHT = booleanPreferencesKey("sudoku_same_digit_highlight")
    }
}
