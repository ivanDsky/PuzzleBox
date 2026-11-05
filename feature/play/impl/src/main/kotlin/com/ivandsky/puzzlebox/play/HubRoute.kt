package com.ivandsky.puzzlebox.play

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ivandsky.puzzlebox.model.GameId
import org.koin.compose.koinInject

/**
 * The Play tab's root (A5, N2): the games hub lists the [PlaySessionStore]'s playable
 * games in registry order. Picking one is [onPick]; the entry pushes that game's key, so
 * back from the board returns here (N3). The hub never opens or fetches a play.
 */
@Composable
internal fun HubRoute(onPick: (GameId) -> Unit, modifier: Modifier = Modifier) {
    val store: PlaySessionStore = koinInject()
    GamePicker(games = store.playable, onPick = onPick, modifier = modifier)
}
