package com.ivandsky.puzzlebox.games.tangoui

import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.engine.TangoHint
import com.ivandsky.puzzlebox.games.tango.render.TangoRendererModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * TalkBack phrasing + violation copy (design-direction §9/§6, floors §10.3/§10.6):
 * pure-string units, including the redundancy floor — the violation state lives in
 * the semantics stateDescription, never pixels alone. Violations are derived from
 * the LIVE grid by [TangoSemantics.violations] (review I2/I3): state-driven, the
 * specific broken edge carries the caption, satisfied edges never over-mark.
 */
class TangoSemanticsTest {

    private fun model(
        cells: Map<Int, TangoCell> = emptyMap(),
        givens: Set<Int> = emptySet(),
        markers: List<TangoRendererModel.MarkerRender> = emptyList(),
        status: TangoRendererModel.Status = TangoRendererModel.Status.PLAYING,
        hintCell: Int? = null,
        hintWhy: TangoHint.Why? = null,
    ) = TangoRendererModel(
        n = 6,
        cells = (0 until 36).map { idx ->
            TangoRendererModel.CellRender(value = cells[idx], isGiven = idx in givens)
        },
        markers = markers,
        rejection = null, // marks are scan-derived now; the transient rejection is play-state's concern
        status = status,
        hintCell = hintCell,
        hintWhy = hintWhy,
    )

    // ---- §9 per-cell strings ----

    @Test
    fun `content description names row and column one-based`() {
        assertEquals("Row 3, column 5", TangoSemantics.cellContentDescription(2, 4))
    }

    @Test
    fun `state description distinguishes given by words not visuals`() {
        val m = model(cells = mapOf(0 to TangoCell.SUN, 1 to TangoCell.MOON), givens = setOf(0))
        assertEquals("given sun", TangoSemantics.cellStateDescription(m, 0))
        assertEquals("moon", TangoSemantics.cellStateDescription(m, 1))
        assertEquals("empty", TangoSemantics.cellStateDescription(m, 2))
    }

    /** §10.6 redundancy: violation rides the stateDescription prefix. */
    @Test
    fun `violation state is carried by the state description`() {
        val m = model(cells = mapOf(0 to TangoCell.SUN, 1 to TangoCell.SUN, 2 to TangoCell.SUN))
        assertEquals(
            "violates: Three suns in a row.",
            TangoSemantics.cellStateDescription(m, 2),
        )
        // Offending cells only — untouched cells stay plain.
        assertEquals("empty", TangoSemantics.cellStateDescription(m, 10))
    }

    /** I3: the scan reads the LIVE grid — marks persist with no rejection event at all. */
    @Test
    fun `violations are state driven not rejection transient`() {
        // rejection is null in every model() here; the run still marks + captions.
        val m = model(cells = mapOf(6 to TangoCell.SUN, 7 to TangoCell.SUN, 8 to TangoCell.SUN))
        assertEquals("violates: Three suns in a row.", TangoSemantics.cellStateDescription(m, 6))
        assertEquals("Three suns in a row.", TangoSemantics.firstViolationPhrase(m))
        // And it clears the moment the board becomes legal again.
        val legal = model(cells = mapOf(6 to TangoCell.SUN, 7 to TangoCell.SUN, 8 to TangoCell.MOON))
        assertNull(TangoSemantics.firstViolationPhrase(legal))
        assertEquals("moon", TangoSemantics.cellStateDescription(legal, 8))
    }

    // ---- §6 violation copy, exact ----

    @Test
    fun `three in a row copy names the axis and value`() {
        val sunRow = model(cells = mapOf(6 to TangoCell.SUN, 7 to TangoCell.SUN, 8 to TangoCell.SUN))
        assertEquals("Three suns in a row.", TangoSemantics.violationPhraseAt(sunRow, 8))
        assertEquals("Three suns in a row.", TangoSemantics.violationPhraseAt(sunRow, 6))
        val moonColumn = model(
            cells = mapOf(3 to TangoCell.MOON, 9 to TangoCell.MOON, 15 to TangoCell.MOON),
        )
        assertEquals("Three moons in a column.", TangoSemantics.violationPhraseAt(moonColumn, 15))
    }

    @Test
    fun `count exceeded copy names the line and the cap`() {
        val cells = (0 until 6).associateWith { TangoCell.SUN }
        val m = model(cells = cells)
        assertEquals("Row 1 has 6 suns — a row holds 3.", TangoSemantics.violationPhraseAt(m, 4))
        val columnCells = (0 until 6).associate { it * 6 to TangoCell.SUN }
        val columnModel = model(cells = columnCells)
        assertEquals("Column 1 has 6 suns — a column holds 3.", TangoSemantics.violationPhraseAt(columnModel, 18))
    }

    @Test
    fun `marker conflict copy names the symbol and requirement`() {
        // The scan needs ACTUAL conflicting values on the edge (I2): same-marker whose
        // two cells differ.
        val same = model(
            cells = mapOf(0 to TangoCell.SUN, 1 to TangoCell.MOON),
            markers = listOf(TangoRendererModel.MarkerRender(0, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME)),
        )
        assertEquals("= pair mismatch — these cells must match.", TangoSemantics.violationPhraseAt(same, 0))
        assertEquals("= pair mismatch — these cells must match.", TangoSemantics.violationPhraseAt(same, 1))
        // Opposite-marker whose two cells match.
        val opposite = model(
            cells = mapOf(0 to TangoCell.SUN, 6 to TangoCell.SUN),
            markers = listOf(TangoRendererModel.MarkerRender(0, TangoEdgeSide.DOWN, TangoMarkerKind.OPPOSITE)),
        )
        assertEquals("× pair mismatch — these cells must differ.", TangoSemantics.violationPhraseAt(opposite, 6))
    }

    /** I2: the SPECIFIC broken edge marks + captions — satisfied edges stay silent. */
    @Test
    fun `broken edge marks its own two cells not every marker`() {
        // Cell 0 carries two markers: RIGHT= (satisfied: sun/sun) and DOWN × (broken:
        // sun/sun). Only the × edge's cells {0, 6} mark, and the caption is the × one.
        val m = model(
            cells = mapOf(0 to TangoCell.SUN, 1 to TangoCell.SUN, 6 to TangoCell.SUN),
            markers = listOf(
                TangoRendererModel.MarkerRender(0, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME),
                TangoRendererModel.MarkerRender(0, TangoEdgeSide.DOWN, TangoMarkerKind.OPPOSITE),
            ),
        )
        assertEquals("× pair mismatch — these cells must differ.", TangoSemantics.violationPhraseAt(m, 0))
        assertEquals("× pair mismatch — these cells must differ.", TangoSemantics.violationPhraseAt(m, 6))
        // Satisfied = edge stays plain.
        assertNull(TangoSemantics.violationPhraseAt(m, 1))
        // And the reverse: satisfied × (sun/moon) with broken = (sun/moon) captions "=".
        val flip = model(
            cells = mapOf(0 to TangoCell.SUN, 1 to TangoCell.MOON, 6 to TangoCell.MOON),
            markers = listOf(
                TangoRendererModel.MarkerRender(0, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME),
                TangoRendererModel.MarkerRender(0, TangoEdgeSide.DOWN, TangoMarkerKind.OPPOSITE),
            ),
        )
        assertEquals("= pair mismatch — these cells must match.", TangoSemantics.violationPhraseAt(flip, 0))
        assertNull(TangoSemantics.violationPhraseAt(flip, 6))
    }

    @Test
    fun `legal grids carry no phrase`() {
        val m = model(cells = mapOf(0 to TangoCell.SUN))
        assertNull(TangoSemantics.violationPhraseAt(m, 5))
        assertNull(TangoSemantics.violationPhraseAt(model(), 3))
        assertNull(TangoSemantics.firstViolationPhrase(m))
    }

    /** §6 priority: the caption picks the first scan violation deterministically. */
    @Test
    fun `caption picks the first violation in scan order`() {
        // Row run (row 1: cells 6..8) and a broken marker edge — markers scan first.
        val m = model(
            cells = mapOf(
                0 to TangoCell.SUN, 1 to TangoCell.MOON, // broken = edge on row 0
                6 to TangoCell.SUN, 7 to TangoCell.SUN, 8 to TangoCell.SUN, // run on row 1
            ),
            markers = listOf(TangoRendererModel.MarkerRender(0, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME)),
        )
        assertEquals("= pair mismatch — these cells must match.", TangoSemantics.firstViolationPhrase(m))
    }

    // ---- §9 marker phrasing on affected cells ----

    @Test
    fun `marker phrases append to affected cells only`() {
        val m = model(
            markers = listOf(
                TangoRendererModel.MarkerRender(0, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME),
                TangoRendererModel.MarkerRender(0, TangoEdgeSide.DOWN, TangoMarkerKind.OPPOSITE),
            ),
        )
        assertEquals(
            "Row 1, column 1; same as the cell to the right; opposite to the cell below",
            listOf(TangoSemantics.cellContentDescription(0, 0))
                .plus(TangoSemantics.markerPhrases(m, 0))
                .joinToString(separator = "; "),
        )
        assertEquals(
            "Row 2, column 1; opposite to the cell below",
            listOf(TangoSemantics.cellContentDescription(1, 0))
                .plus(TangoSemantics.markerPhrases(m, 6))
                .joinToString(separator = "; "),
        )
        assertEquals(emptyList<String>(), TangoSemantics.markerPhrases(m, 20))
    }

    // ---- §6 hint copy, exact value-naming (M1) ----

    @Test
    fun `hint copy names the line and both values`() {
        val m = model(cells = mapOf(0 to TangoCell.MOON, 1 to TangoCell.MOON))
        assertEquals(
            "Row 1 would hold three moons — this cell is a sun.",
            TangoSemantics.hintPhrase(m, TangoHint.Why.ThreeInARow(first = 0, second = 1)),
        )
        assertEquals(
            "Column 5 already has three suns — the rest are moons.",
            TangoSemantics.hintPhrase(
                m,
                TangoHint.Why.LineFull(row = null, col = 4, fullOf = TangoCell.SUN),
            ),
        )
    }

    @Test
    fun `hint marker copy names the bound value`() {
        val m = model(cells = mapOf(0 to TangoCell.SUN, 1 to TangoCell.MOON))
        assertEquals(
            "= links this cell to a sun — it must be a sun.",
            TangoSemantics.hintPhrase(
                m,
                TangoHint.Why.MarkerBinding(markerCell = 0, neighborIdx = 1, kind = TangoMarkerKind.SAME),
            ),
        )
        assertEquals(
            "× links this cell to a sun — it must be a moon.",
            TangoSemantics.hintPhrase(
                m,
                TangoHint.Why.MarkerBinding(markerCell = 0, neighborIdx = 1, kind = TangoMarkerKind.OPPOSITE),
            ),
        )
    }
}
