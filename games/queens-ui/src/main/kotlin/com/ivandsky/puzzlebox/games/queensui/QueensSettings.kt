package com.ivandsky.puzzlebox.games.queensui

import kotlinx.coroutines.flow.Flow

/**
 * The player's Queens settings, as the Queens controller sees them: [autoCross] is on while
 * placing a queen fills in the squares it rules out (on until the player turns it off in the ⋮
 * menu). The app binds this to the persisted game settings; the module never reads storage.
 */
interface QueensSettings {
    val autoCross: Flow<Boolean>

    suspend fun setAutoCross(enabled: Boolean)
}
