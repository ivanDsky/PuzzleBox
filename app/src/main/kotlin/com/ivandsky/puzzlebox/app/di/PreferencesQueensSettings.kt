package com.ivandsky.puzzlebox.app.di

import com.ivandsky.puzzlebox.data.repository.GameSettingsRepository
import com.ivandsky.puzzlebox.games.queensui.QueensSettingKeys
import com.ivandsky.puzzlebox.games.queensui.QueensSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/**
 * Queens' settings over the persisted game settings; the UI module only sees [QueensSettings].
 * [autoCross] is collected eagerly in [scope] (the app's), so it holds the stored value by the time a board opens.
 */
class PreferencesQueensSettings(private val settings: GameSettingsRepository, scope: CoroutineScope) : QueensSettings {
    override val autoCross: StateFlow<Boolean> = settings.flag(QueensSettingKeys.AutoCross)
        .stateIn(scope, SharingStarted.Eagerly, QueensSettingKeys.AutoCross.default)

    override suspend fun setAutoCross(enabled: Boolean) =
        withContext(NonCancellable) { settings.setFlag(QueensSettingKeys.AutoCross, enabled) }
}
