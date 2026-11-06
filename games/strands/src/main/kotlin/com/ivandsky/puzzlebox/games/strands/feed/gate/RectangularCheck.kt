package com.ivandsky.puzzlebox.games.strands.feed.gate

import java.time.LocalDate

/** A non-empty board whose rows all have the same, non-zero length. */
object RectangularCheck : StrandsTrustCheck {
    override fun problem(wire: CompleteWire, requested: LocalDate): String? = when {
        wire.rows == 0 -> "empty board"
        wire.cols == 0 || wire.board.any { it.length != wire.cols } -> "board is not rectangular"
        else -> null
    }
}
