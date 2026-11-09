package com.ivandsky.puzzlebox.games.sudokuui

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Settings that live as long as the object, both on at the start. */
class InMemorySudokuHighlightSettings : SudokuHighlightSettings {
    private val peerState = MutableStateFlow(true)
    private val sameDigitState = MutableStateFlow(true)
    override val peer: Flow<Boolean> = peerState
    override val sameDigit: Flow<Boolean> = sameDigitState

    override suspend fun setPeer(enabled: Boolean) {
        peerState.value = enabled
    }

    override suspend fun setSameDigit(enabled: Boolean) {
        sameDigitState.value = enabled
    }
}
