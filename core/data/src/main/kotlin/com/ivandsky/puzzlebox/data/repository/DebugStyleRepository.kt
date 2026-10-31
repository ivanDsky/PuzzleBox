package com.ivandsky.puzzlebox.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Debug-only style prototype persistence (ticket 08): which typography/palette variant
 * the switcher last selected, stored as enum-name strings. This layer stays agnostic
 * of the variant enums — the app maps names through the designsystem's `fromStored`.
 *
 * Lives in its own DataStore file (debug_style.preferences_pb), NOT the production
 * preferences file, so the shipped schema never gains prototype keys and a release
 * build — which never resolves this binding — never touches the file.
 */
interface DebugStyleRepository {
    val typographyVariantName: Flow<String?>
    val paletteVariantName: Flow<String?>

    suspend fun setTypographyVariantName(name: String)
    suspend fun setPaletteVariantName(name: String)
}

/**
 * Preferences DataStore implementation. Reads tolerate a missing or corrupt value by
 * yielding null (the caller resolves the default variant); writes store the raw name.
 */
class DataStoreDebugStyleRepository(
    private val dataStore: DataStore<Preferences>,
) : DebugStyleRepository {

    override val typographyVariantName: Flow<String?> = dataStore.data
        .map { preferences -> preferences[KEY_TYPOGRAPHY_VARIANT] }

    override val paletteVariantName: Flow<String?> = dataStore.data
        .map { preferences -> preferences[KEY_PALETTE_VARIANT] }

    override suspend fun setTypographyVariantName(name: String) {
        dataStore.edit { preferences -> preferences[KEY_TYPOGRAPHY_VARIANT] = name }
    }

    override suspend fun setPaletteVariantName(name: String) {
        dataStore.edit { preferences -> preferences[KEY_PALETTE_VARIANT] = name }
    }

    private companion object {
        val KEY_TYPOGRAPHY_VARIANT = stringPreferencesKey("debug_typography_variant")
        val KEY_PALETTE_VARIANT = stringPreferencesKey("debug_palette_variant")
    }
}
