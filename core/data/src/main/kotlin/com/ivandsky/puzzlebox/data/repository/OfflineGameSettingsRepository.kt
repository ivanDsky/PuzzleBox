package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.datastore.PuzzleBoxPreferencesDataSource
import com.ivandsky.puzzlebox.model.GameSetting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Per-game settings on the core:datastore data source. */
internal class OfflineGameSettingsRepository(
    private val dataSource: PuzzleBoxPreferencesDataSource,
) : GameSettingsRepository {

    override fun flag(setting: GameSetting): Flow<Boolean> =
        dataSource.gameFlag(setting.game, setting.name, setting.default).distinctUntilChanged()

    override suspend fun setFlag(setting: GameSetting, enabled: Boolean) {
        dataSource.setGameFlag(setting.game, setting.name, enabled)
    }
}
