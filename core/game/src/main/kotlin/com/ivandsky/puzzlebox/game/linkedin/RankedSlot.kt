package com.ivandsky.puzzlebox.game.linkedin

/**
 * One spot on a board that PuzzleBox rows and LinkedIn ghosts share: [index] points into the list of rows, or into the
 * list of ghosts when [ghost] is set. [place] is null for a ghost whose time is hidden, which is unranked.
 */
data class RankedSlot(val ghost: Boolean, val index: Int, val place: Int?)
