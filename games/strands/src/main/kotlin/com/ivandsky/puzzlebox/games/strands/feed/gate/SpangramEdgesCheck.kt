package com.ivandsky.puzzlebox.games.strands.feed.gate

import java.time.LocalDate

/** The spangram touches two opposite sides: top and bottom, or left and right. */
object SpangramEdgesCheck : StrandsTrustCheck {
    override fun problem(wire: CompleteWire, requested: LocalDate): String? {
        val rows = wire.spangramCoords.map { it[0] }
        val cols = wire.spangramCoords.map { it[1] }
        val topToBottom = 0 in rows && wire.rows - 1 in rows
        val leftToRight = 0 in cols && wire.cols - 1 in cols
        return if (topToBottom || leftToRight) null else "spangram does not span the board"
    }
}
