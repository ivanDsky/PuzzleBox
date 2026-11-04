package com.ivandsky.puzzlebox.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ivandsky.puzzlebox.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Everything the app keeps in its Preferences DataStore file. */
data class PuzzleBoxPreferences(
    val themeMode: ThemeMode,
)

/**
 * Owns the Preferences DataStore keys. Reads tolerate a missing or corrupt value by
 * falling back to [ThemeMode.SYSTEM]; writes store the enum name so the file stays
 * human-readable and forward-compatible.
 */
class PuzzleBoxPreferencesDataSource(
    private val dataStore: DataStore<Preferences>,
) {

    val preferences: Flow<PuzzleBoxPreferences> = dataStore.data.map { preferences ->
        PuzzleBoxPreferences(
            themeMode = preferences[KEY_THEME_MODE]?.let(ThemeMode::fromStored) ?: ThemeMode.SYSTEM,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { preferences -> preferences[KEY_THEME_MODE] = mode.name }
    }

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
    }
}
