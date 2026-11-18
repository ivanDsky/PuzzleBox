package com.ivandsky.puzzlebox.play

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.model.PlaySlot
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
 *
 * Tutorials (spec A11) are this entry's, not the store's: its [TutorialGateHolder] opens them
 * in place of the board, and How to play reopens them over the live play. While they show, the board's
 * composition is gone, so the play chrome's clock effect has folded the clock; it reopens when the board
 * returns. The lessons run in a sandbox: nothing they do reaches the slot or the records.
 */
class GameViewModel internal constructor(
    key: GameKey,
    store: PlaySessionStore,
    calendar: GameCalendar,
    prefs: UserPreferencesRepository,
    howToPlayRules: HowToPlayRules,
) : ViewModel() {

    /** The slot's live play; null when the key's game is unplayable in this build. */
    val play: ActivePlay? = store.open(key)

    /**
     * True while a today key's pinned board is no longer the game's today (A9): the board
     * was kept across its cadence-zone midnight, so the bar labels it as yesterday's. A
     * key with an explicit day is a play again and never is.
     */
    val pastDay: StateFlow<Boolean> = (play?.takeIf { key.day == null }?.slot as? PlaySlot.Daily)?.let { slot ->
        calendar.observeToday(slot.gameId)
            .map { today -> today != slot.day }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)
    } ?: MutableStateFlow(false).asStateFlow()

    private val gate = TutorialGateHolder(play, prefs, viewModelScope, howToPlayRules)

    internal val tutorial: StateFlow<TutorialGate> = gate.gate

    /** The rules sheet's copy while it shows (RulesSheet on, a game with rules), else null. */
    internal val rules: StateFlow<RulesSheetCopy?> = gate.rules

    /** How to play: the rules sheet (RulesSheet on) or the game's tutorial opens over the live play. */
    fun howToPlay() = gate.howToPlay()

    /** The tutorial ended (finished or skipped): the game is marked seen and the board shows. */
    fun closeTutorial() = gate.close()

    /** The rules sheet closed: the board plays on. */
    fun dismissRules() = gate.dismissRules()

    /** Replay the tutorial from the rules sheet. */
    fun replayTutorial() = gate.replayTutorial()

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
