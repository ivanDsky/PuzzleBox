package com.ivandsky.puzzlebox.resultui

import com.ivandsky.puzzlebox.game.BestScore

/**
 * What the player's earlier solves of the very puzzle on screen say (any source, this solve left out): the [previous]
 * solve, newest first, and the [best] of them all by the game's own order. [previousFromLinkedIn] is true when the
 * previous solve is a LinkedIn import. [read] is false while the rows are not read yet, so the hero says nothing
 * rather than a wording it would then change. [None] is a puzzle with no earlier solve. The hero draws the previous and
 * best solve as two labelled columns.
 */
data class SamePuzzleSolves(
    val previous: BestScore?,
    val previousFromLinkedIn: Boolean = false,
    val best: BestScore? = null,
    val read: Boolean = true,
) {
    companion object {
        val None = SamePuzzleSolves(previous = null)
        val Unread = SamePuzzleSolves(previous = null, read = false)
    }
}
