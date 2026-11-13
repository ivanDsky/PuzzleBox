package com.ivandsky.puzzlebox.games.tangoui

import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.engine.TangoVerdict
import com.ivandsky.puzzlebox.games.tango.render.TangoRendererModel
import com.ivandsky.puzzlebox.games.tango.render.TangoViolations

/**
 * TalkBack phrasing (design-direction §9) + the STATE-DRIVEN violation projection
 * (§6: marks "persist until the board is legal again" — derived from the live grid,
 * never from the transient last-rejection event) + §6-exact violation/hint copy —
 * pure functions so the §10.6 redundancy floor is unit-asserted on the JVM.
 */
object TangoSemantics {

    // ---- per-cell TalkBack strings (§9) ----

    /** ContentDescription: "Row 3, column 5" (1-based, TalkBack convention). */
    fun cellContentDescription(row: Int, col: Int): String =
        "Row ${row + 1}, column ${col + 1}"

    /**
     * StateDescription: "given sun" / "sun" / "moon" / "empty", prefixed "violates: "
     * + rule phrase when the cell carries a violation on the live grid (§10.6
     * redundancy — the state-driven scan, not the transient rejection). A guided
     * lesson's ghost adds its [ghostPhrase], so TalkBack hears what the board shows.
     */
    fun cellStateDescription(model: TangoRendererModel, idx: Int): String {
        val render = model.cells.getOrNull(idx)
        val value = when (render?.value) {
            TangoCell.SUN -> if (render.isGiven) "given sun" else "sun"
            TangoCell.MOON -> if (render.isGiven) "given moon" else "moon"
            null -> "empty"
        }
        val violation = violationPhraseAt(model, idx)
        val state = if (violation == null) value else "violates: $violation"
        return model.ghosts[idx]?.let { "$state, ${ghostPhrase(it)}" } ?: state
    }

    /** The ghost's spoken nudge on a guided square: "try a moon". */
    fun ghostPhrase(answer: TangoCell): String = "try a ${singular(answer)}"

    /**
     * The rule-naming phrase for the offending cell on the LIVE grid, or null when
     * the cell carries no violation (§6: the copy tells the truth about the cause).
     * Per-cell priority: a broken edge first, then an over-full line, then the run —
     * a 6-sun row reads as the count truth, not the run consequence.
     */
    fun violationPhraseAt(model: TangoRendererModel, idx: Int): String? =
        TangoViolations.of(model)
            .filter { idx in it.cells }
            .minByOrNull {
                when (it.reason) {
                    TangoVerdict.Reason.MarkerConflict -> 0
                    TangoVerdict.Reason.CountExceeded -> 1
                    else -> 2
                }
            }
            ?.phrase

    /** Caption-slot phrase: the first violation in deterministic scan order, or null. */
    fun firstViolationPhrase(model: TangoRendererModel): String? =
        TangoViolations.of(model).firstOrNull()?.phrase

    /**
     * Marker phrasing appended to each affected cell's ContentDescription (§9): the
     * RIGHT/DOWN edge reads "same as the cell to the right/below" /
     * "opposite to the cell to the right/below" — never a focusable marker node (A12).
     * BOTH cells of the edge are affected (§6: "both cells marked").
     */
    fun markerPhrases(model: TangoRendererModel, idx: Int): List<String> =
        model.markers.filter { markerInvolved(it, idx, model.n) }.map { marker ->
            val neighbor = when (marker.side) {
                TangoEdgeSide.RIGHT -> "the cell to the right"
                TangoEdgeSide.DOWN -> "the cell below"
            }
            when (marker.kind) {
                TangoMarkerKind.SAME -> "same as $neighbor"
                TangoMarkerKind.OPPOSITE -> "opposite to $neighbor"
            }
        }

    /** Whether the cell sits on a broken marker edge (renderer tint/border gate). */
    fun markerInvolved(marker: TangoRendererModel.MarkerRender, idx: Int, n: Int): Boolean {
        val row = marker.cellIdx / n
        val col = marker.cellIdx % n
        return when (marker.side) {
            TangoEdgeSide.RIGHT -> idx == marker.cellIdx || idx == row * n + col + 1
            TangoEdgeSide.DOWN -> idx == marker.cellIdx || idx == marker.cellIdx + n
        }
    }

    /** Custom action labels (§9): the gesture model; commits are final-only (I4). */
    val customActions = listOf("Place sun", "Place moon", "Clear")

    private fun singular(value: TangoCell?): String = when (value) {
        TangoCell.MOON -> "moon"
        else -> "sun"
    }
}
