package com.ivandsky.puzzlebox.create.editor

import com.ivandsky.puzzlebox.game.editor.Difficulty
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The level the author last asked Finish for (90), kept for the session: one instance serves
 * every Studio the process opens, so the picker opens on the last choice; Medium until there is one.
 */
class FinishLevelChoice {
    private val chosen = MutableStateFlow(Difficulty.Level.Medium)
    val level: StateFlow<Difficulty.Level> = chosen.asStateFlow()

    fun choose(level: Difficulty.Level) {
        chosen.value = level
    }
}
