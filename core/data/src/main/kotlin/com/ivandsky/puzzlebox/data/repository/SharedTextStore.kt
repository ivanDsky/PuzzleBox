package com.ivandsky.puzzlebox.data.repository

import kotlinx.coroutines.flow.StateFlow

/**
 * Text another app shared to PuzzleBox (ACTION_SEND text/plain). In memory only; never
 * persisted, never in a NavKey (P8).
 */
interface SharedTextStore {
    /** The waiting text, or null. */
    val pending: StateFlow<String?>
    fun put(text: String)
    /** Returns the waiting text and clears it; null when none. */
    fun take(): String?
}
