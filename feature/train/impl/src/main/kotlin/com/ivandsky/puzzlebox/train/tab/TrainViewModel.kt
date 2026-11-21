package com.ivandsky.puzzlebox.train.tab

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.data.trainer.TechniqueProgress
import com.ivandsky.puzzlebox.data.trainer.TrainerProgressRepository
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.gameui.trainer.TrainerGame
import com.ivandsky.puzzlebox.gameui.trainer.TrainerRegistry
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val STOP_TIMEOUT_MS = 5_000L

private const val KEY_GAME = "train.open.game"
private const val KEY_TECHNIQUE = "train.open.technique"
private const val KEY_ATTEMPT = "train.open.attempt"

/** The open technique as [SavedStateHandle] keeps it: ids, resolved against the registry when shown. */
private data class OpenKey(val game: GameId, val technique: TechniqueId, val attempt: Int)

/**
 * The Train pane's state: the games that have a trainer, the chosen game, its techniques with their marks, and which
 * technique's practice board is open. The chosen game is the last one the player picked ([UserPreferencesRepository.lastTrainGame])
 * while the [registry] still has it, else the registry's first (spec A13); picking a game stores it. Marks follow
 * [progress]; a technique never answered reads New (A5). The open technique's ids live in [savedState], so turning the
 * device and a restore after the system closed the app both keep which technique is open; the practice board is the
 * practice view model's own, and starts again after a restore (A15).
 *
 * The tablet's list and right pane and the phone's single page all read this one view model.
 */
internal class TrainViewModel(
    private val registry: TrainerRegistry,
    private val prefs: UserPreferencesRepository,
    private val progress: TrainerProgressRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val open = MutableStateFlow(restoreOpen())
    private var attempts = savedState.get<Int>(KEY_ATTEMPT) ?: 0

    /** Null until the stored game and its progress have been read. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<TrainState?> = prefs.lastTrainGame
        .map { stored -> stored?.let(registry::get) ?: registry.all.firstOrNull() }
        .filterNotNull()
        .distinctUntilChanged { a, b -> a.gameId == b.gameId }
        .flatMapLatest { game -> progress.observe(game.gameId).map { marks -> game to marks } }
        .combine(open) { (game, marks), key -> trainState(game, marks, key) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** Shows [game]'s techniques and remembers the pick. A technique open in another game closes. Ignored for a game with no trainer. */
    fun selectGame(game: GameId) {
        if (registry[game] == null) return
        if (open.value?.game != game) setOpen(null)
        viewModelScope.launch { prefs.setLastTrainGame(game) }
    }

    /** Opens [technique] of [game] and starts its practice board; opening the technique already open leaves its board as it is. */
    fun openTechnique(game: GameId, technique: TechniqueId) {
        val current = open.value
        if (current != null && current.game == game && current.technique == technique) return
        val trainer = registry[game] ?: return
        if (trainer.catalog().none { it.id == technique }) return
        attempts += 1
        savedState[KEY_ATTEMPT] = attempts
        setOpen(OpenKey(game, technique, attempts))
    }

    /** Closes the open technique's practice board. */
    fun closeTechnique() = setOpen(null)

    private fun setOpen(key: OpenKey?) {
        savedState[KEY_GAME] = key?.game?.value
        savedState[KEY_TECHNIQUE] = key?.technique?.value
        open.value = key
    }

    /** The technique left open before a rotation or a restore, if the registry still has its game and the game its technique. */
    private fun restoreOpen(): OpenKey? {
        val game = savedState.get<String>(KEY_GAME)?.let(::GameId) ?: return null
        val technique = savedState.get<String>(KEY_TECHNIQUE)?.let(::TechniqueId) ?: return null
        val trainer = registry[game] ?: return null
        if (trainer.catalog().none { it.id == technique }) return null
        return OpenKey(game, technique, savedState.get<Int>(KEY_ATTEMPT) ?: 0)
    }

    private fun trainState(game: TrainerGame, marks: Map<TechniqueId, TechniqueProgress>, key: OpenKey?) = TrainState(
        games = registry.all.map { it.gameId },
        selected = game.gameId,
        techniques = game.catalog().map { TechniqueRow(it, marks[it.id] ?: TechniqueProgress.NEW) },
        open = key?.let(::resolve),
    )

    private fun resolve(key: OpenKey): OpenTechnique? {
        val trainer = registry[key.game] ?: return null
        val technique = trainer.catalog().firstOrNull { it.id == key.technique } ?: return null
        return OpenTechnique(trainer, technique, key.attempt)
    }
}
