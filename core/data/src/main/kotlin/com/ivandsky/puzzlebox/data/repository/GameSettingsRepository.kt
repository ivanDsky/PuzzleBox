package com.ivandsky.puzzlebox.data.repository

import com.ivandsky.puzzlebox.model.GameSetting
import kotlinx.coroutines.flow.Flow

/** Per-game boolean settings; each game declares its own [GameSetting]s, core names none. */
interface GameSettingsRepository {
    /** The stored value of [setting], or its default while never set. */
    fun flag(setting: GameSetting): Flow<Boolean>

    suspend fun setFlag(setting: GameSetting, enabled: Boolean)
}
