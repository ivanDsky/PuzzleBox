package com.ivandsky.puzzlebox.play

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.TutorialSession
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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

    /**
     * [ui]'s tutorial (spec A11) runs [session]: before the first board of a game whose
     * tutorial is not seen ([over] null), or from How to play over [over], the live play,
     * whose clock is paused meanwhile.
     */
    data class Tutorial(val ui: GamePlayUi, val session: TutorialSession, val over: ActivePlay?) : PlayUiState
}

/**
 * The play route's state holder over the [PlaySessionStore] single: the store owns the
 * live play (it outlives this entry across the result route) and the kept sessions;
 * this VM maps it and [todaySet] to [PlayUiState] and forwards the route's intents.
 * The picker's rows are seeded from [games] so the first frame already lists every
 * game; their statuses land when [todaySet] emits (it reads Room and DataStore).
 *
 * The open tutorial lives here, not in the composition, so a rotation keeps its step.
 * Its lessons run in [com.ivandsky.puzzlebox.gameui.TutorialSandbox]: nothing they do
 * reaches the store, the kept sessions or the records.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayViewModel(
    private val store: PlaySessionStore,
    todaySet: ObserveTodaySet,
    games: GameRegistry,
    private val plays: GamePlayRegistry,
    private val prefs: UserPreferencesRepository,
) : ViewModel() {

    private val today: StateFlow<List<PlayUiState.Picker.Row>> = todaySet()
        .map { entries -> entries.map { PlayUiState.Picker.Row(it.game, it.displayName, it.status) } }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            games.all.map { PlayUiState.Picker.Row(it.definition.id, it.definition.displayName, status = null) },
        )

    private val tutorial = MutableStateFlow<PlayUiState.Tutorial?>(null)

    // The first-play close starts the game, which suspends; a second tap waits for it.
    private var closing: Job? = null

    private val board: StateFlow<PlayUiState> = store.active
        .flatMapLatest { play -> play?.board?.map { board -> playing(play, board) } ?: today.map(PlayUiState::Picker) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, current())

    val state: StateFlow<PlayUiState> = combine(tutorial, board) { open, board -> open ?: board }
        .stateIn(viewModelScope, SharingStarted.Eagerly, tutorial.value ?: board.value)

    /** The live play, read synchronously by the route's restore/load effect. */
    val active: ActivePlay? get() = store.active.value

    /**
     * Picks [game]: its tutorial first when it has one the player has not seen,
     * otherwise its kept session of today resumes, or it starts fresh.
     */
    fun pick(game: GameId, onSessionChanged: (ByteArray) -> Unit) {
        viewModelScope.launch {
            val ui = plays[game]
            if (ui != null && ui.tutorial.isNotEmpty() && !prefs.tutorialSeen(game).first()) {
                if (tutorial.value == null) tutorial.value = PlayUiState.Tutorial(ui, TutorialSession(ui.tutorial), over = null)
            } else {
                store.pick(game, onSessionChanged)
            }
        }
    }

    /** How to play: the live play's tutorial opens over it and its clock pauses. */
    fun howToPlay() {
        if (tutorial.value != null) return
        val play = store.active.value ?: return
        if (play.ui.tutorial.isEmpty()) return
        store.pauseClock(play)
        tutorial.value = PlayUiState.Tutorial(play.ui, TutorialSession(play.ui.tutorial), over = play)
    }

    /**
     * The tutorial ended (finished or skipped): the game is marked seen. Over a live play
     * its clock resumes where it paused; on a first play the picked game starts now,
     * exactly as a pick does, and its sink is [onSessionChanged].
     */
    fun closeTutorial(onSessionChanged: (ByteArray) -> Unit) {
        val open = tutorial.value ?: return
        if (closing?.isActive == true) return
        val game = open.ui.gameId
        viewModelScope.launch { prefs.markTutorialSeen(game) }
        val over = open.over
        if (over != null) {
            store.resumeClock(over)
            tutorial.value = null
            return
        }
        // The tutorial stays up until the game is live, so the picker never flashes.
        closing = viewModelScope.launch {
            store.pick(game, onSessionChanged)
            tutorial.value = null
        }
    }

    /** Adopts [envelope]; a tutorial over any other play (a replay arrived) closes. */
    fun restore(envelope: SessionBlob.Decoded?, onSessionChanged: (ByteArray) -> Unit) {
        val play = store.restore(envelope, onSessionChanged) ?: return
        tutorial.update { open -> open?.takeIf { it.over === play } }
    }

    /** Back out of the play: the session is kept, an open tutorial closes unseen, and the
     *  next entry shows the picker. */
    fun leave() {
        tutorial.value = null
        store.leave()
    }

    /** The app went to the background (ON_STOP): the live session is kept. */
    fun background() = store.background()

    /** A new entry over the result's finished [play]: drop it, so the picker shows. */
    fun dropFinished(play: ActivePlay) = store.dropFinished(play)

    private fun current(): PlayUiState =
        store.active.value?.let { playing(it, it.board.value) } ?: PlayUiState.Picker(today.value)

    private fun playing(play: ActivePlay, board: ActivePlay.Board) =
        PlayUiState.Playing(play, play.ui, board.controller, board.status)
}
