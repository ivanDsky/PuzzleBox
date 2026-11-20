package com.ivandsky.puzzlebox.games.queens.engine.trainer

import com.ivandsky.puzzlebox.games.queens.Cell

/** A move the practice board judges (spec §2.2): a queen or a cross on [cell]. Taking a queen or a cross back is never judged. */
sealed interface QueensPracticeMove {
    val cell: Cell

    data class Place(override val cell: Cell) : QueensPracticeMove

    data class Cross(override val cell: Cell) : QueensPracticeMove
}
