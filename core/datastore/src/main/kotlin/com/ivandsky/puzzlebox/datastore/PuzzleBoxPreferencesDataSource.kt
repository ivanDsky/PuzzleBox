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
)

/**
 * Owns the Preferences DataStore keys. Reads tolerate a missing or corrupt value by
 * falling back to [ThemeMode.SYSTEM]; writes store the enum name so the file stays
 * human-readable and forward-compatible. Seen tutorials are one string set of game ids.
 * Auto-show is a plain boolean, true while the key is absent.
 */
class PuzzleBoxPreferencesDataSource(
    private val dataStore: DataStore<Preferences>,
) {

    val preferences: Flow<PuzzleBoxPreferences> = dataStore.data.map { preferences ->
        PuzzleBoxPreferences(
            themeMode = preferences[KEY_THEME_MODE]?.let(ThemeMode::fromStored) ?: ThemeMode.SYSTEM,
            tutorialsSeen = preferences[KEY_TUTORIALS_SEEN].orEmpty().mapTo(mutableSetOf(), ::GameId),
            autoShowWitness = preferences[KEY_AUTO_SHOW_WITNESS] ?: true,
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

    /** A game's own boolean setting; [default] while it was never written. */
    fun gameFlag(game: GameId, name: String, default: Boolean): Flow<Boolean> =
        dataStore.data.map { it[gameFlagKey(game, name)] ?: default }

    suspend fun setGameFlag(game: GameId, name: String, enabled: Boolean) {
        dataStore.edit { it[gameFlagKey(game, name)] = enabled }
    }

    private fun gameFlagKey(game: GameId, name: String) = booleanPreferencesKey("game.${game.value}.$name")

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_TUTORIALS_SEEN = stringSetPreferencesKey("tutorials_seen")
        val KEY_AUTO_SHOW_WITNESS = booleanPreferencesKey("auto_show_witness")
    }
}
