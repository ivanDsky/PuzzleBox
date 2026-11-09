package com.ivandsky.puzzlebox.games.queensui

import kotlinx.coroutines.flow.MutableStateFlow

/** A [QueensSettings] held in memory: [stored] is the saved value, [writes] every [setAutoCross] call in order. */
class InMemoryQueensSettings(initialAutoCross: Boolean = true) : QueensSettings {
    val stored = MutableStateFlow(initialAutoCross)

    val writes = ArrayList<Boolean>()

    override val autoCross = stored

    override suspend fun setAutoCross(enabled: Boolean) {
        writes += enabled
        stored.value = enabled
    }
}
