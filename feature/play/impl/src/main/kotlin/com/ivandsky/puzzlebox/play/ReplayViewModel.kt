package com.ivandsky.puzzlebox.play

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.domain.LoadSolveReplay
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.replay.Timeline
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.replay.GameReplayBoard
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.api.ReplayKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** What the replay page shows: nothing yet, the timeline to play, or no replay for any cause (A13). */
internal sealed interface ReplayState {
    data object Loading : ReplayState

    data object None : ReplayState

    data class Ready(val timeline: Timeline) : ReplayState
}

/**
 * The state holder of the replay page for [key]. It reads the timeline once when it opens, through [LoadSolveReplay],
 * so a key (ids only) is all a surface passes and the page survives process death. Read-only.
 */
internal class ReplayViewModel(
    private val key: ReplayKey,
    loadReplay: LoadSolveReplay,
    private val games: GameRegistry,
    ui: GamePlayRegistry,
    private val calendar: GameCalendar,
    private val preferences: UserPreferencesRepository,
) : ViewModel() {

    private val gameId = GameId(key.gameId)

    private val _state = MutableStateFlow<ReplayState>(ReplayState.Loading)
    val state: StateFlow<ReplayState> = _state

    private val showErrorsHolder = ReplayShowErrors(preferences)

    /** The "Show errors" switch: one global preference, read before the page leaves Loading and written on every change. */
    val showErrors: StateFlow<Boolean> = showErrorsHolder.value

    fun setShowErrors(on: Boolean) {
        showErrorsHolder.set(on, viewModelScope)
    }

    /** The game's replay board; null for a game that has none, which the page shows as no replay. */
    val board: GameReplayBoard? = ui[gameId]?.replayBoard

    /** The page's "Game · where it came from" line; the page appends the move count. */
    val context: String get() = "${games.displayName(gameId)} · ${key.origin(calendar)}"

    init {
        viewModelScope.launch {
            showErrorsHolder.load()
            val ref = key.toSolveReplayRef(games)
            val timeline = ref?.let { loadReplay(it) }
            _state.value = if (timeline == null) ReplayState.None else ReplayState.Ready(timeline)
        }
    }
}
