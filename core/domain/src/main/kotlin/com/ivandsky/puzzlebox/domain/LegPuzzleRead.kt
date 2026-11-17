package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.game.Puzzle

/** What one read of a duel leg's puzzle concluded: [Absent] is a settled no, [Unavailable] a read that failed and a retry may answer. */
sealed interface LegPuzzleRead {
    data class Found(val puzzle: Puzzle) : LegPuzzleRead
    data object Absent : LegPuzzleRead
    data object Unavailable : LegPuzzleRead
}
