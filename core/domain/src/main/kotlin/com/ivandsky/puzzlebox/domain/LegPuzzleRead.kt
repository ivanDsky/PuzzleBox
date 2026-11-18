package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.game.Puzzle

/**
 * What one read of a duel leg's puzzle concluded: [Absent] is a settled no, [TakenDown] a friend's puzzle its author
 * withdrew that this device never saved (also settled), [Unavailable] a read that failed and a retry may answer.
 */
sealed interface LegPuzzleRead {
    data class Found(val puzzle: Puzzle) : LegPuzzleRead
    data object Absent : LegPuzzleRead
    data object TakenDown : LegPuzzleRead
    data object Unavailable : LegPuzzleRead
}
