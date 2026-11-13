package com.ivandsky.puzzlebox.games.tango.render

import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.TangoSession
import com.ivandsky.puzzlebox.games.tango.engine.TangoState
import com.ivandsky.puzzlebox.games.tango.engine.TangoVerdict

/**
 * The STATE-DRIVEN violation scan (§6: marks "persist until the board is legal again" — derived
 * from the grid, never from the transient last-rejection event). One scan for live play, the
 * replay board and the replay timeline, so all three agree on what is violated.
 */
object TangoViolations {

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
    fun of(model: TangoRendererModel): List<Violation> {
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

    /** The violating cell-sets of [session]'s board, via the shared scan (no suppression applied). */
    fun cellSets(session: TangoSession): Set<Set<Int>> =
        of(TangoRendererModel.of(state = TangoState(session.puzzle, session.grid()))).mapTo(mutableSetOf()) { it.cells }

    /** The broken edge's caption, in the lesson's own words (= the same, × different). */
    fun markerConflictPhrase(kind: TangoMarkerKind): String = when (kind) {
        TangoMarkerKind.SAME -> "These two must be the same."
        TangoMarkerKind.OPPOSITE -> "These two must be different."
    }

    private fun plural(value: TangoCell): String = if (value == TangoCell.SUN) "suns" else "moons"
}
