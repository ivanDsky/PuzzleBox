package com.ivandsky.puzzlebox.play

import androidx.compose.runtime.Composable
import com.ivandsky.puzzlebox.model.GameId

/**
 * Runs the Community screen's [content] with the create action: [CustomSlot.newPuzzle]'s host around it when the host
 * gave one, [content] given the host's create call (null: the New puzzle sheet; a game: a new draft of it). With no
 * host [content] runs with null, and the screen draws no FAB.
 */
@Composable
internal fun WithNewPuzzle(custom: CustomSlot, content: @Composable (onNewPuzzle: ((GameId?) -> Unit)?) -> Unit) {
    val host = custom.newPuzzle
    if (host != null) host(CreateFabClearance) { open -> content(open) } else content(null)
}
