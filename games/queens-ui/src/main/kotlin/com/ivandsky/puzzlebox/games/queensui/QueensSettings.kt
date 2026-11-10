package com.ivandsky.puzzlebox.games.queensui

import kotlinx.coroutines.flow.StateFlow

/**
 * The player's Queens settings, as the Queens controller sees them: [autoCross] is on while
 * placing a queen fills in the squares it rules out (on until the player turns it off in the ⋮
 * menu). The app binds this to the persisted game settings; the module never reads storage.
 */
interface QueensSettings {
    /** Primed before any board opens, so a controller seeds from [StateFlow.value] and the first frame is already right. */
    val autoCross: StateFlow<Boolean>

    /** Runs to completion even if the caller is cancelled, so a flip followed at once by leaving the board still sticks. */
    suspend fun setAutoCross(enabled: Boolean)
}
