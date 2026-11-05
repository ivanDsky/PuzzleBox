package com.ivandsky.puzzlebox.play

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.gameui.TutorialSession
import com.ivandsky.puzzlebox.play.api.GameKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One [GameKey] entry's state holder (A9, N21): opens the key's slot in the
 * [PlaySessionStore] once, when the entry is created. The store owns the live play, so
 * every entry naming the same slot shares its controller, and the play outlives this
 * ViewModel; releasing it is [PlaySessionStore.retain]'s job, never this one's.
 *
 * Tutorials (spec A11) are this entry's, not the store's: a game with lessons whose
 * tutorial [prefs] has not marked seen opens it before the board, and How to play
 * reopens it over the live play. While it shows, the board's composition is gone, so the
 * play chrome's clock effect has folded the clock; it reopens when the board returns.
 * The lessons run in a sandbox: nothing they do reaches the slot or the records. The open
 * tutorial lives here, so a rotation keeps its step.
 */
class GameViewModel(
    key: GameKey,
    store: PlaySessionStore,
    calendar: GameCalendar,
    private val prefs: UserPreferencesRepository,
) : ViewModel() {

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

    private val hasLessons = play?.ui?.tutorial?.isNotEmpty() == true

    private val _tutorial = MutableStateFlow(if (hasLessons) TutorialGate.Checking else TutorialGate.Closed)
    internal val tutorial: StateFlow<TutorialGate> = _tutorial.asStateFlow()

    init {
        if (play != null && hasLessons) {
            viewModelScope.launch {
                val seen = prefs.tutorialSeen(play.gameId).first()
                if (_tutorial.value == TutorialGate.Checking) {
                    _tutorial.value = if (seen) TutorialGate.Closed else TutorialGate.Open(TutorialSession(play.ui.tutorial))
                }
            }
        }
    }

    /** How to play: the game's tutorial opens over the live play. A no-op without lessons
     *  or while a tutorial is already open. */
    fun howToPlay() {
        val lessons = play?.ui?.tutorial ?: return
        if (lessons.isEmpty() || _tutorial.value is TutorialGate.Open) return
        _tutorial.value = TutorialGate.Open(TutorialSession(lessons))
    }

    /** The tutorial ended (finished or skipped): the game is marked seen and the board shows. */
    fun closeTutorial() {
        val game = play?.gameId ?: return
        if (_tutorial.value !is TutorialGate.Open) return
        _tutorial.value = TutorialGate.Closed
        viewModelScope.launch { prefs.markTutorialSeen(game) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
