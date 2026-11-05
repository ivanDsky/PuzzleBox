package com.ivandsky.puzzlebox.play

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.play.api.GameKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * One [GameKey] entry's state holder (A9, N21): opens the key's slot in the
 * [PlaySessionStore] once, when the entry is created. The store owns the live play, so
 * every entry naming the same slot shares its controller, and the play outlives this
 * ViewModel; releasing it is [PlaySessionStore.retain]'s job, never this one's.
 */
class GameViewModel(key: GameKey, store: PlaySessionStore, calendar: GameCalendar) : ViewModel() {

    /** The slot's live play; null when the key's game is unplayable in this build. */
    val play: ActivePlay? = store.open(key)

    /**
     * True while a today key's pinned board is no longer the game's today (A9): the board
     * was kept across its cadence-zone midnight, so the bar labels it as yesterday's. A
     * key with an explicit day is a replay and never is.
     */
    val pastDay: StateFlow<Boolean> = play?.takeIf { key.day == null }?.slot?.let { slot ->
        calendar.observeToday(slot.gameId)
            .map { today -> today != slot.day }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)
    } ?: MutableStateFlow(false).asStateFlow()

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
