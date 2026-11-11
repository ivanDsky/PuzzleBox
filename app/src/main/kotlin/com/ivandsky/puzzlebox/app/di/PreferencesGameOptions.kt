package com.ivandsky.puzzlebox.app.di

import com.ivandsky.puzzlebox.data.repository.GameSettingsRepository
import com.ivandsky.puzzlebox.games.queensui.QueensSettingKeys
import com.ivandsky.puzzlebox.games.sudokuui.SudokuSettings
import com.ivandsky.puzzlebox.profile.settings.GameOptions
import kotlinx.coroutines.flow.Flow

/** Settings' Games switches over the same stored keys the in-game toggles use, so both places always agree. */
class PreferencesGameOptions(private val settings: GameSettingsRepository) : GameOptions {
    override val queensAutoCross: Flow<Boolean> = settings.flag(QueensSettingKeys.AutoCross)
    override val sudokuPeerHighlight: Flow<Boolean> = settings.flag(SudokuSettings.PeerHighlight)
    override val sudokuSameDigitHighlight: Flow<Boolean> = settings.flag(SudokuSettings.SameDigitHighlight)

    override suspend fun setQueensAutoCross(enabled: Boolean) = settings.setFlag(QueensSettingKeys.AutoCross, enabled)

    override suspend fun setSudokuPeerHighlight(enabled: Boolean) = settings.setFlag(SudokuSettings.PeerHighlight, enabled)

    override suspend fun setSudokuSameDigitHighlight(enabled: Boolean) =
        settings.setFlag(SudokuSettings.SameDigitHighlight, enabled)
}
