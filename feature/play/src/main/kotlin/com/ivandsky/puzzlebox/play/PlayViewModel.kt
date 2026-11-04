package com.ivandsky.puzzlebox.play

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** What the play route draws. */
sealed interface PlayUiState {
    /** Fresh entry (A14): the playable games, in registry order, with display names. */
    data class Picker(val games: List<Pair<GameId, String>>) : PlayUiState

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
 * live play (it outlives this entry across the result route); this VM only maps it to
 * [PlayUiState] and forwards the route's intents.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayViewModel(private val store: PlaySessionStore) : ViewModel() {

    val state: StateFlow<PlayUiState> = store.active
        .flatMapLatest { play -> play?.board?.map { board -> playing(play, board) } ?: flowOf(picker()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, current())

    /** The live play, read synchronously by the route's restore/load effect. */
    val active: ActivePlay? get() = store.active.value

    fun pick(game: GameId, onSessionChanged: (ByteArray) -> Unit) {
        store.start(game, onSessionChanged)
    }

    fun restore(envelope: SessionBlob.Decoded?, onSessionChanged: (ByteArray) -> Unit) {
        store.restore(envelope, onSessionChanged)
    }

    fun end() = store.end()

    private fun current(): PlayUiState = store.active.value?.let { playing(it, it.board.value) } ?: picker()

    private fun picker() = PlayUiState.Picker(store.playable)

    private fun playing(play: ActivePlay, board: ActivePlay.Board) =
        PlayUiState.Playing(play, play.ui, board.controller, board.status)
}
