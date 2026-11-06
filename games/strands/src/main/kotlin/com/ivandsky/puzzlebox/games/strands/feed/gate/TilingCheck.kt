package com.ivandsky.puzzlebox.games.strands.feed.gate

import java.time.LocalDate

/** The theme words and the spangram cover every cell exactly once. */
object TilingCheck : StrandsTrustCheck {
    override fun problem(wire: CompleteWire, requested: LocalDate): String? {
        val cells = wire.allWordCells()
        val distinct = cells.toSet()
        return when {
            distinct.size != cells.size -> "words overlap"
            distinct.size != wire.rows * wire.cols -> "${wire.rows * wire.cols - distinct.size} cells uncovered"
            else -> null
        }
    }
}
