package com.ivandsky.puzzlebox.testing.repository

import com.ivandsky.puzzlebox.data.repository.GameSettingsRepository
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.GameSetting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory [GameSettingsRepository]: a value per (game, name), the setting's default while unset. */
class FakeGameSettingsRepository : GameSettingsRepository {

    private val values = MutableStateFlow<Map<Pair<GameId, String>, Boolean>>(emptyMap())

    override fun flag(setting: GameSetting): Flow<Boolean> =
        values.map { it[setting.game to setting.name] ?: setting.default }.distinctUntilChanged()

    override suspend fun setFlag(setting: GameSetting, enabled: Boolean) {
        values.update { it + ((setting.game to setting.name) to enabled) }
    }
}
