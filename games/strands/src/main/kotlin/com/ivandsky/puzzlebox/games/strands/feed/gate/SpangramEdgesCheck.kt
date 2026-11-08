package com.ivandsky.puzzlebox.games.strands.feed.gate

import java.time.LocalDate

/** The spangram touches two opposite sides: top and bottom, or left and right. */
object SpangramEdgesCheck : StrandsTrustCheck {
    override fun problem(wire: CompleteWire, requested: LocalDate): String? =
        if (spans(wire.spangramCoords, wire.rows, wire.cols)) null else "spangram does not span the board"

    /**
     * Whether the spangram reaches two opposite board edges, given its `[row, col]` coords on a
     * board of [rows] × [cols]. Shared with the editor's publish gate.
     */
    fun spans(spangramCoords: List<List<Int>>, rows: Int, cols: Int): Boolean {
        val touchedRows = spangramCoords.map { it[0] }
        val touchedCols = spangramCoords.map { it[1] }
        return (0 in touchedRows && rows - 1 in touchedRows) || (0 in touchedCols && cols - 1 in touchedCols)
    }
}
