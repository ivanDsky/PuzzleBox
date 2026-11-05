package com.ivandsky.puzzlebox.play

import androidx.lifecycle.ViewModel
import com.ivandsky.puzzlebox.play.api.GameKey

/**
 * One [GameKey] entry's state holder (A9, N21): opens the key's slot in the
 * [PlaySessionStore] once, when the entry is created. The store owns the live play, so
 * every entry naming the same slot shares its controller, and the play outlives this
 * ViewModel; releasing it is [PlaySessionStore.retain]'s job, never this one's.
 */
class GameViewModel(key: GameKey, store: PlaySessionStore) : ViewModel() {

    /** The slot's live play; null when the key's game is unplayable in this build. */
    val play: ActivePlay? = store.open(key)
}
