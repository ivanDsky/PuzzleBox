package com.ivandsky.puzzlebox.app.di

import com.ivandsky.puzzlebox.data.repository.GameSettingsRepository
import com.ivandsky.puzzlebox.games.sudokuui.SudokuHighlightSettings
import com.ivandsky.puzzlebox.games.sudokuui.SudokuSettings
import kotlinx.coroutines.flow.Flow

/** Sudoku's two tint toggles over the persisted game settings; the UI module only sees [SudokuHighlightSettings]. */
class PreferencesSudokuHighlightSettings(private val settings: GameSettingsRepository) : SudokuHighlightSettings {
    override val peer: Flow<Boolean> = settings.flag(SudokuSettings.PeerHighlight)
    override val sameDigit: Flow<Boolean> = settings.flag(SudokuSettings.SameDigitHighlight)

    override suspend fun setPeer(enabled: Boolean) = settings.setFlag(SudokuSettings.PeerHighlight, enabled)

    override suspend fun setSameDigit(enabled: Boolean) = settings.setFlag(SudokuSettings.SameDigitHighlight, enabled)
}
