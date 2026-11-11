package com.ivandsky.puzzlebox.profile.settings

import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [GameOptions]; every switch starts on like the stored defaults, and the flows are writable for "changed elsewhere". */
internal class FakeGameOptions : GameOptions {
    override val queensAutoCross = MutableStateFlow(true)
    override val sudokuPeerHighlight = MutableStateFlow(true)
    override val sudokuSameDigitHighlight = MutableStateFlow(true)

    override suspend fun setQueensAutoCross(enabled: Boolean) {
        queensAutoCross.value = enabled
    }

    override suspend fun setSudokuPeerHighlight(enabled: Boolean) {
        sudokuPeerHighlight.value = enabled
    }

    override suspend fun setSudokuSameDigitHighlight(enabled: Boolean) {
        sudokuSameDigitHighlight.value = enabled
    }
}
