package com.ivandsky.puzzlebox.games.tangoui

import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.engine.TangoHint
import com.ivandsky.puzzlebox.games.tango.engine.TangoVerdict
import com.ivandsky.puzzlebox.games.tango.render.TangoRendererModel

/**
 * TalkBack phrasing (design-direction §9) + the STATE-DRIVEN violation projection
 * (§6: marks "persist until the board is legal again" — derived from the live grid,
 * never from the transient last-rejection event) + §6-exact violation/hint copy —
 * pure functions so the §10.6 redundancy floor is unit-asserted on the JVM.
 */
object TangoSemantics {

    /** One violated constraint on the live grid: which cells, which rule, which edge. */
    data class Violation(
        val cells: Set<Int>,
        val reason: TangoVerdict.Reason,
        /** The broken `=`/`×` edge, when [reason] is [TangoVerdict.Reason.MarkerConflict]. */
        val marker: TangoRendererModel.MarkerRender?,
        val phrase: String,
    )

    // ---- state-driven violation scan (§6, review I2/I3) ----

    /**
     * Every violated constraint on the CURRENT grid: broken `=`/`×` edges first, then
     * three-in-a-row runs, then over-full lines. Empty exactly when the board is
     * legal — marks derived from this persist while anything is illegal.
     */
    fun violations(model: TangoRendererModel): List<Violation> {
        val n = model.n
        val found = mutableListOf<Violation>()

        // Broken marker edges — the SPECIFIC edge is carried so satisfied edges never
        // over-mark and a dual-marked cell captions the actually-broken symbol (I2).
        for (marker in model.markers) {
            val a = valueAt(model, marker.cellIdx) ?: continue
            val b = valueAt(model, neighbor(marker, n) ?: continue) ?: continue
            val broken = when (marker.kind) {
                TangoMarkerKind.SAME -> a != b
                TangoMarkerKind.OPPOSITE -> a == b
            }
            if (broken) {
                found += Violation(
                    cells = setOf(marker.cellIdx, neighbor(marker, n)!!),
                    reason = TangoVerdict.Reason.MarkerConflict,
                    marker = marker,
                    phrase = markerConflictPhrase(marker.kind),
                )
            }
        }

        // Three-in-a-row runs (rows then columns, row-major order).
        for (row in 0 until n) {
            val line = (0 until n).map { row * n + it }
            found += runsIn(model, line, axis = "row", lineIndex = row)
        }
        for (col in 0 until n) {
            val line = (0 until n).map { col + it * n }
            found += runsIn(model, line, axis = "column", lineIndex = col)
        }

        // Over-full lines (§6 count copy names the line, value, and cap).
        val half = n / 2
        for (row in 0 until n) {
            val line = (0 until n).map { row * n + it }
            found += overFull(model, line, half, isRow = true)
        }
        for (col in 0 until n) {
            val line = (0 until n).map { col + it * n }
            found += overFull(model, line, half, isRow = false)
        }
        // Settle debounce, diff-based (user QA 2026-11-02): only violations newly
        // introduced by the last placement wait for the settle window; pre-existing
        // ones stay visible until a grid change actually removes them.
        return found.filterNot { it.cells in model.suppressedSets }
    }

    private fun runsIn(
        model: TangoRendererModel,
        line: List<Int>,
        axis: String,
        lineIndex: Int,
    ): List<Violation> {
        val violations = mutableListOf<Violation>()
        var run = mutableListOf<Int>()
        var runValue: TangoCell? = null
        fun flush() {
            if (runValue != null && run.size >= 3) {
                violations += Violation(
                    cells = run.toSet(),
                    reason = TangoVerdict.Reason.ThreeInARow,
                    marker = null,
                    phrase = "Three ${plural(runValue!!)} in a $axis.",
                )
            }
            run = mutableListOf()
            runValue = null
        }
        for (idx in line) {
            val value = valueAt(model, idx)
            if (value == null || value != runValue) flush()
            if (value != null) {
                run.add(idx)
                runValue = value
            }
        }
        flush()
        return violations
    }

    private fun overFull(
        model: TangoRendererModel,
        line: List<Int>,
        half: Int,
        isRow: Boolean,
    ): List<Violation> {
        val n = model.n
        return listOf(TangoCell.SUN, TangoCell.MOON).mapNotNull { value ->
            val placed = line.filter { valueAt(model, it) == value }
            if (placed.size > half) {
                val name = if (isRow) "Row ${line.first() / n + 1}" else "Column ${line.first() % n + 1}"
                val axis = if (isRow) "row" else "column"
                Violation(
                    cells = placed.toSet(),
                    reason = TangoVerdict.Reason.CountExceeded,
                    marker = null,
                    phrase = "$name has ${placed.size} ${plural(value)} — a $axis holds $half.",
                )
            } else {
                null
            }
        }
    }

    private fun neighbor(marker: TangoRendererModel.MarkerRender, n: Int): Int? {
        val row = marker.cellIdx / n
        val col = marker.cellIdx % n
        return when (marker.side) {
            TangoEdgeSide.RIGHT -> if (col + 1 < n) marker.cellIdx + 1 else null
            TangoEdgeSide.DOWN -> if (row + 1 < n) marker.cellIdx + n else null
        }
    }

    private fun valueAt(model: TangoRendererModel, idx: Int): TangoCell? =
        model.cells.getOrNull(idx)?.value

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
        violations(model)
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
        violations(model).firstOrNull()?.phrase

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

    /** The broken edge's caption, in the lesson's own words (= the same, × different). */
    internal fun markerConflictPhrase(kind: TangoMarkerKind): String = when (kind) {
        TangoMarkerKind.SAME -> "These two must be the same."
        TangoMarkerKind.OPPOSITE -> "These two must be different."
    }

    /** Custom action labels (§9): the gesture model; commits are final-only (I4). */
    val customActions = listOf("Place sun", "Place moon", "Clear")
    // ---- hint copy (§6, exact value-naming; review M1) ----

    /**
     * The hint's WHY as a §6 sentence, value-naming with evidence from the grid:
     * "Row 3 would hold three moons — this cell is a sun." /
     * "Column 5 already has three suns — the rest are moons." /
     * "= links this cell to a sun — it must be a sun."
     */
    fun hintPhrase(model: TangoRendererModel, why: TangoHint.Why): String = when (why) {
        is TangoHint.Why.MarkerBinding -> {
            val bound = valueAt(model, why.markerCell)
                ?: valueAt(model, why.neighborIdx)
            val word = singular(bound) ?: "cell"
            when (why.kind) {
                TangoMarkerKind.SAME -> "= links this cell to a $word — it must be a $word."
                TangoMarkerKind.OPPOSITE -> {
                    val other = if (bound == TangoCell.SUN) "moon" else "sun"
                    "× links this cell to a $word — it must be a $other."
                }
            }
        }
        is TangoHint.Why.ThreeInARow -> {
            val value = valueAt(model, why.first)
            val n = model.n
            val sameRow = why.first / n == why.second / n
            val line = if (sameRow) "Row ${why.first / n + 1}" else "Column ${why.first % n + 1}"
            "$line would hold three ${plural(value ?: TangoCell.SUN)} — " +
                "this cell is a ${singular(other(value ?: TangoCell.SUN))}."
        }
        is TangoHint.Why.LineFull -> {
            val row = why.row
            val line = if (row != null) "Row ${row + 1}" else "Column ${(why.col ?: 0) + 1}"
            val half = numberWord(model.n / 2)
            "$line already has $half ${plural(why.fullOf)} — the rest are ${plural(other(why.fullOf))}."
        }
        is TangoHint.Why.LineFills -> {
            val row = why.row
            val line = if (row != null) "Row ${row + 1}" else "Column ${(why.col ?: 0) + 1}"
            if (why.fills == 1) {
                "$line has only one way to finish — this cell follows from it."
            } else {
                "$line has only ${why.fills} ways to finish, and all of them agree on this cell."
            }
        }
    }

    private fun numberWord(count: Int): String = when (count) {
        2 -> "two"
        3 -> "three"
        4 -> "four"
        5 -> "five"
        else -> count.toString()
    }

    private fun singular(value: TangoCell?): String = when (value) {
        TangoCell.MOON -> "moon"
        else -> "sun"
    }

    private fun plural(value: TangoCell): String = if (value == TangoCell.SUN) "suns" else "moons"

    private fun other(value: TangoCell): TangoCell = if (value == TangoCell.SUN) TangoCell.MOON else TangoCell.SUN
}
