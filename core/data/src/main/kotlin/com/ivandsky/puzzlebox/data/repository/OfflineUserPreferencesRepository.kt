package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.datastore.PuzzleBoxPreferencesDataSource
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** On-device preferences, read and written through the core:datastore data source. */
internal class OfflineUserPreferencesRepository(
    private val dataSource: PuzzleBoxPreferencesDataSource,
) : UserPreferencesRepository {

    override val themeMode: Flow<ThemeMode> = dataSource.preferences.map { it.themeMode }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataSource.setThemeMode(mode)
    }

    override fun tutorialSeen(game: GameId): Flow<Boolean> =
        dataSource.preferences.map { game in it.tutorialsSeen }.distinctUntilChanged()

    override suspend fun markTutorialSeen(game: GameId) {
        dataSource.markTutorialSeen(game)
    }

    override val autoShowWitness: Flow<Boolean> =
        dataSource.preferences.map { it.autoShowWitness }.distinctUntilChanged()

    override suspend fun setAutoShowWitness(on: Boolean) {
        dataSource.setAutoShowWitness(on)
    }

    override val showReplayErrors: Flow<Boolean> =
        dataSource.preferences.map { it.showReplayErrors }.distinctUntilChanged()

    override suspend fun setShowReplayErrors(on: Boolean) {
        dataSource.setShowReplayErrors(on)
    }
}
