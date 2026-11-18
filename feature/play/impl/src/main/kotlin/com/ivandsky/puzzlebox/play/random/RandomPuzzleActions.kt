package com.ivandsky.puzzlebox.play.random

import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.play.community.DifficultyChoice

/**
 * What the random sheet's body can ask for: a source, a game (null is Any), "Not solved yet" on or off, a difficulty
 * (null is Any) and a pick.
 */
internal data class RandomPuzzleActions(
    val onSource: (RandomSource) -> Unit,
    val onGame: (GameId?) -> Unit,
    val onUnsolved: (Boolean) -> Unit,
    val onDifficulty: (DifficultyChoice?) -> Unit,
    val onPick: () -> Unit,
)
