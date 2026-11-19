package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.datastore.PuzzleBoxPreferencesDataSource
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ThemeMode
import com.ivandsky.puzzlebox.model.ThemeRead
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** On-device preferences, read and written through the core:datastore data source. */
internal class OfflineUserPreferencesRepository(
    private val dataSource: PuzzleBoxPreferencesDataSource,
) : UserPreferencesRepository {

    override val themeMode: Flow<ThemeMode> = dataSource.preferences.map { it.themeMode }

    override val themeRead: Flow<ThemeRead> = dataSource.themeRead

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

    override val ranksFriendsDefault: Flow<Boolean> =
        dataSource.preferences.map { it.ranksFriendsDefault }.distinctUntilChanged()

    override suspend fun setRanksFriendsDefault(friends: Boolean) {
        dataSource.setRanksFriendsDefault(friends)
    }

    override val ranksLinkedInDefault: Flow<Boolean> =
        dataSource.preferences.map { it.ranksLinkedInDefault }.distinctUntilChanged()

    override suspend fun setRanksLinkedInDefault(linkedIn: Boolean) {
        dataSource.setRanksLinkedInDefault(linkedIn)
    }

    override val vibration: Flow<Boolean> =
        dataSource.preferences.map { it.vibration }.distinctUntilChanged()

    override suspend fun setVibration(on: Boolean) {
        dataSource.setVibration(on)
    }

    override val showSelfDuelTimeLeft: Flow<Boolean> =
        dataSource.preferences.map { it.showSelfDuelTimeLeft }.distinctUntilChanged()

    override suspend fun setShowSelfDuelTimeLeft(on: Boolean) {
        dataSource.setShowSelfDuelTimeLeft(on)
    }
}
