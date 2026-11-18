package com.ivandsky.puzzlebox.domain.lineup

import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.model.ShareCode

/**
 * One row the add sheet can show: the [entry] to add, whether it is [isNew], whether the player has a solve of it, its [meta] line
 * (the size) and its difficulty [level], null for a puzzle with no label.
 */
data class PickablePuzzle(
    val entry: ExactPuzzle,
    val isNew: Boolean,
    val played: Boolean,
    val meta: String,
    val code: ShareCode?,
    val level: Difficulty.Level? = null,
)
