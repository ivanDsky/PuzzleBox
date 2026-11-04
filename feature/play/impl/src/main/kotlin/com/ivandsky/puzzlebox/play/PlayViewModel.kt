package com.ivandsky.puzzlebox.play

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the play route draws. */
sealed interface PlayUiState {
    /** Fresh entry (A14): today's set — every registered game, in registry order, with
     *  its status (not started / in progress / solved). */
    data class Picker(val games: List<Row>) : PlayUiState {
        /** One picker row; [status] is null until today's set first emits (no subtitle). */
        data class Row(val game: GameId, val displayName: String, val status: TodayStatus?)
    }

    /** A live play: [ui] draws [controller] under the shared fetch [status]. */
    data class Playing(
        val play: ActivePlay,
        val ui: GamePlayUi,
        val controller: GamePlayController,
        val status: PlayBoardStatus,
    ) : PlayUiState
}

/**
 * The play route's state holder over the [PlaySessionStore] single: the store owns the
 * live play (it outlives this entry across the result route) and the kept sessions;
 * this VM maps it and [todaySet] to [PlayUiState] and forwards the route's intents.
 * The picker's rows are seeded from [games] so the first frame already lists every
 * game; their statuses land when [todaySet] emits (it reads Room and DataStore).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayViewModel(
    private val store: PlaySessionStore,
    todaySet: ObserveTodaySet,
    games: GameRegistry,
) : ViewModel() {

    private val today: StateFlow<List<PlayUiState.Picker.Row>> = todaySet()
        .map { entries -> entries.map { PlayUiState.Picker.Row(it.game, it.displayName, it.status) } }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            games.all.map { PlayUiState.Picker.Row(it.definition.id, it.definition.displayName, status = null) },
        )

    val state: StateFlow<PlayUiState> = store.active
        .flatMapLatest { play -> play?.board?.map { board -> playing(play, board) } ?: today.map(PlayUiState::Picker) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, current())

    /** The live play, read synchronously by the route's restore/load effect. */
    val active: ActivePlay? get() = store.active.value

    /** Picks [game]: its kept session of today resumes, otherwise it starts fresh. */
    fun pick(game: GameId, onSessionChanged: (ByteArray) -> Unit) {
        viewModelScope.launch { store.pick(game, onSessionChanged) }
    }

    fun restore(envelope: SessionBlob.Decoded?, onSessionChanged: (ByteArray) -> Unit) {
        store.restore(envelope, onSessionChanged)
    }

    /** Back out of the play: the session is kept and the next entry shows the picker. */
    fun leave() = store.leave()

    /** The app went to the background (ON_STOP): the live session is kept. */
    fun background() = store.background()

    /** A new entry over the result's finished [play]: drop it, so the picker shows. */
    fun dropFinished(play: ActivePlay) = store.dropFinished(play)

    private fun current(): PlayUiState =
        store.active.value?.let { playing(it, it.board.value) } ?: PlayUiState.Picker(today.value)

    private fun playing(play: ActivePlay, board: ActivePlay.Board) =
        PlayUiState.Playing(play, play.ui, board.controller, board.status)
}
