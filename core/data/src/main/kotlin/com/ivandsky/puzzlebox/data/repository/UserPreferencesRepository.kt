package com.ivandsky.puzzlebox.data.repository

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
}
