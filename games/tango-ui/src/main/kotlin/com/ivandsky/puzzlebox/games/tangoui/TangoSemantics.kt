package com.ivandsky.puzzlebox.games.tangoui

import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.engine.TangoVerdict
import com.ivandsky.puzzlebox.games.tango.render.TangoRendererModel

/**
 * TalkBack phrasing (design-direction §9, verbatim copy §6) and violation captions —
 * pure functions so the §10.6 redundancy floor is unit-asserted on the JVM: the
 * violation state lives in the semantics stateDescription, never pixels alone.
 */
object TangoSemantics {

    // ---- per-cell TalkBack strings (§9) ----

    /** ContentDescription: "Row 3, column 5" (1-based, TalkBack convention). */
    fun cellContentDescription(row: Int, col: Int): String =
        "Row ${row + 1}, column ${col + 1}"

    /**
     * StateDescription: "given sun" / "sun" / "moon" / "empty", prefixed "violates: "
     * + rule phrase when the cell carries the violation (redundant channel, §10.6).
     */
    fun cellStateDescription(model: TangoRendererModel, idx: Int): String {
        val render = model.cells.getOrNull(idx)
        val value = when (render?.value) {
            TangoCell.SUN -> if (render.isGiven) "given sun" else "sun"
            TangoCell.MOON -> if (render.isGiven) "given moon" else "moon"
            null -> "empty"
        }
        val violation = violationPhrase(model, idx)
        return if (violation == null) value else "violates: $violation"
    }

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

    /** Custom action labels (§9): variant A maps to cycle steps, B to chooser + erase. */
    val customActions = listOf("Place sun", "Place moon", "Clear")

    /** Chooser target labels (§9, variant B). */
    const val chooserSunLabel = "Place sun here"
    const val chooserMoonLabel = "Place moon here"
    const val chooserDismissLabel = "Cancel"

    /** Count rail semantics (§9): "Row 3 has 2 of 3 suns". */
    fun rowCountDescription(row: Int, placed: Int, total: Int): String =
        "Row ${row + 1} has $placed of $total suns"

    fun columnCountDescription(col: Int, placed: Int, total: Int): String =
        "Column ${col + 1} has $placed of $total suns"

    // ---- violation copy (§6, exact) ----

    /**
     * The rule-naming phrase for the offending cell, or null when the cell carries
     * no violation. Axis/value derived by scanning the grid — the copy tells the
     * truth about the cause, names the axis/rule.
     */
    fun violationPhrase(model: TangoRendererModel, idx: Int): String? {
        val rejection = model.rejection ?: return null
        val n = model.n
        val row = idx / n
        val col = idx % n
        return when (rejection.reason) {
            TangoVerdict.Reason.ThreeInARow ->
                if (rejection.idx == idx) threeInARowPhrase(model, idx) else null
            TangoVerdict.Reason.CountExceeded ->
                if (rejection.idx == idx) countExceededPhrase(model, idx) else null
            TangoVerdict.Reason.MarkerConflict ->
                // §6: both cells of the broken edge are marked and captioned.
                if (model.markers.any { markerInvolved(it, idx, n) }) markerConflictPhrase(model, idx) else null
            TangoVerdict.Reason.LockedGiven ->
                if (rejection.idx == idx) "This cell is a given." else null
            TangoVerdict.Reason.ForeignMove -> null
        }
    }

    /** Whether the cell carries the rejection's violation (renderer tint/border gate). */
    fun isViolating(model: TangoRendererModel, idx: Int): Boolean = violationPhrase(model, idx) != null

    /** "Three suns in a row." / "Three moons in a column." — axis named. */
    private fun threeInARowPhrase(model: TangoRendererModel, idx: Int): String {
        val value = model.cells.getOrNull(idx)?.value ?: return "Three in a row."
        val name = if (value == TangoCell.SUN) "suns" else "moons"
        val n = model.n
        val row = idx / n
        val col = idx % n
        val rowRun = longestRun(model, row * n, 1, n)
        val colRun = longestRun(model, col, n, n)
        val axis = if (rowRun >= 3) "row" else "column"
        return "Three $name in a $axis."
    }

    /** "Row 4 has 4 suns — a row holds 3." / column variant. */
    private fun countExceededPhrase(model: TangoRendererModel, idx: Int): String {
        val value = model.cells.getOrNull(idx)?.value ?: return "Row count exceeded."
        val name = if (value == TangoCell.SUN) "suns" else "moons"
        val n = model.n
        val row = idx / n
        val col = idx % n
        val half = n / 2
        val rowPlaced = countPlaced(model, row * n, 1, n)
        val colPlaced = countPlaced(model, col, n, n)
        return if (rowPlaced > half) {
            "Row ${row + 1} has $rowPlaced $name — a row holds $half."
        } else {
            "Column ${col + 1} has $colPlaced $name — a column holds $half."
        }
    }

    /** "= pair mismatch — these cells must match." / "× pair mismatch — these cells must differ." */
    private fun markerConflictPhrase(model: TangoRendererModel, idx: Int): String {
        val symbol = model.markers
            .firstOrNull { markerInvolved(it, idx, model.n) }
            ?.let { if (it.kind == TangoMarkerKind.SAME) "=" else "×" } ?: "="
        val requirement = if (symbol == "=") "match" else "differ"
        return "$symbol pair mismatch — these cells must $requirement."
    }

    private fun markerInvolved(marker: TangoRendererModel.MarkerRender, idx: Int, n: Int): Boolean {
        val row = marker.cellIdx / n
        val col = marker.cellIdx % n
        return when (marker.side) {
            TangoEdgeSide.RIGHT -> idx == marker.cellIdx || idx == row * n + col + 1
            TangoEdgeSide.DOWN -> idx == marker.cellIdx || idx == marker.cellIdx + n
        }
    }

    /** Longest same-value run along a line; empty cells break runs. */
    private fun longestRun(model: TangoRendererModel, start: Int, step: Int, count: Int): Int {
        var best = 0
        var run = 0
        var last: TangoCell? = null
        for (i in 0 until count) {
            val value = model.cells.getOrNull(start + i * step)?.value
            run = if (value != null && value == last) run + 1 else if (value != null) 1 else 0
            last = value
            best = maxOf(best, run)
        }
        return best
    }

    private fun countPlaced(model: TangoRendererModel, start: Int, step: Int, count: Int): Int =
        (0 until count).count { model.cells.getOrNull(start + it * step)?.value != null }

    /** Placed suns along a row (count rail semantics + pips). */
    fun sunsInRow(model: TangoRendererModel, row: Int): Int =
        (0 until model.n).count {
            model.cells.getOrNull(row * model.n + it)?.value == TangoCell.SUN
        }

    /** Placed suns along a column. */
    fun sunsInColumn(model: TangoRendererModel, col: Int): Int =
        (0 until model.n).count {
            model.cells.getOrNull(it * model.n + col)?.value == TangoCell.SUN
        }
}
