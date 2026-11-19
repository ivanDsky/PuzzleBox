package com.ivandsky.puzzlebox.games.queens.engine.trainer

import com.ivandsky.puzzlebox.games.queens.Cell

/** The one move a Queens snippet round takes (spec §2.2): a queen or a cross on an empty [cell]. */
sealed interface QueensTrainerMove {
    val cell: Cell

    data class Place(override val cell: Cell) : QueensTrainerMove

    data class Cross(override val cell: Cell) : QueensTrainerMove
}
