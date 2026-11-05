package com.ivandsky.puzzlebox.play

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ivandsky.puzzlebox.domain.ObservePlayProgress
import com.ivandsky.puzzlebox.model.GameId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** One hub row: a playable game, its display name and whether it has a save for today. */
internal data class HubRow(
    val game: GameId,
    val name: String,
    val inProgress: Boolean = false,
)

/**
 * The games hub's state holder: the [PlaySessionStore]'s playable games in registry order,
 * each marked in progress when [ObservePlayProgress] has a save for it today (A13, SC11).
 * Before the first progress emission no row is.
 */
class HubViewModel(store: PlaySessionStore, progress: ObservePlayProgress) : ViewModel() {

    private val playable = store.playable

    internal val rows: StateFlow<List<HubRow>> = progress()
        .map { p -> playable.map { (game, name) -> HubRow(game, name, inProgress = game in p.inProgress) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, playable.map { (game, name) -> HubRow(game, name) })
}
