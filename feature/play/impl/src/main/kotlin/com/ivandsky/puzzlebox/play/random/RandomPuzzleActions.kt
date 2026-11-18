package com.ivandsky.puzzlebox.play.random

import com.ivandsky.puzzlebox.model.GameId

/** What the random sheet's body can ask for: a game (null is Any), "Not solved yet" on or off, and a pick. */
internal data class RandomPuzzleActions(
    val onGame: (GameId?) -> Unit,
    val onUnsolved: (Boolean) -> Unit,
    val onPick: () -> Unit,
)
