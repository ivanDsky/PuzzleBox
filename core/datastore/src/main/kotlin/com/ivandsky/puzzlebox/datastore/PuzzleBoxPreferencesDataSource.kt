package com.ivandsky.puzzlebox.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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
)

/**
 * Owns the Preferences DataStore keys. Reads tolerate a missing or corrupt value by
 * falling back to [ThemeMode.SYSTEM]; writes store the enum name so the file stays
 * human-readable and forward-compatible. Seen tutorials are one string set of game ids.
 */
class PuzzleBoxPreferencesDataSource(
    private val dataStore: DataStore<Preferences>,
) {

    val preferences: Flow<PuzzleBoxPreferences> = dataStore.data.map { preferences ->
        PuzzleBoxPreferences(
            themeMode = preferences[KEY_THEME_MODE]?.let(ThemeMode::fromStored) ?: ThemeMode.SYSTEM,
            tutorialsSeen = preferences[KEY_TUTORIALS_SEEN].orEmpty().mapTo(mutableSetOf(), ::GameId),
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

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_TUTORIALS_SEEN = stringSetPreferencesKey("tutorials_seen")
    }
}
