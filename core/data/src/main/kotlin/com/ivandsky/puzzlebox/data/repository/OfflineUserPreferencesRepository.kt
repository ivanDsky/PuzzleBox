package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.datastore.PuzzleBoxPreferencesDataSource
import com.ivandsky.puzzlebox.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** On-device preferences, read and written through the core:datastore data source. */
internal class OfflineUserPreferencesRepository(
    private val dataSource: PuzzleBoxPreferencesDataSource,
) : UserPreferencesRepository {

    override val themeMode: Flow<ThemeMode> = dataSource.preferences.map { it.themeMode }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataSource.setThemeMode(mode)
    }
}
