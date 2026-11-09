package com.ivandsky.puzzlebox.app.di

import com.ivandsky.puzzlebox.data.repository.GameSettingsRepository
import com.ivandsky.puzzlebox.games.queensui.QueensSettingKeys
import com.ivandsky.puzzlebox.games.queensui.QueensSettings
import kotlinx.coroutines.flow.Flow

/** Queens' settings over the persisted game settings; the UI module only sees [QueensSettings]. */
class PreferencesQueensSettings(private val settings: GameSettingsRepository) : QueensSettings {
    override val autoCross: Flow<Boolean> = settings.flag(QueensSettingKeys.AutoCross)

    override suspend fun setAutoCross(enabled: Boolean) = settings.setFlag(QueensSettingKeys.AutoCross, enabled)
}
