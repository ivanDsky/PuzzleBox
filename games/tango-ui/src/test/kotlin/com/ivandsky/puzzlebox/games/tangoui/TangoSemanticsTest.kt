package com.ivandsky.puzzlebox.games.tangoui

import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.engine.TangoVerdict
import com.ivandsky.puzzlebox.games.tango.render.TangoRendererModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TalkBack phrasing + violation copy (design-direction §9/§6, floors §10.3/§10.6):
 * pure-string units, including the redundancy floor — the violation state lives in
 * the semantics stateDescription, never pixels alone.
 */
class TangoSemanticsTest {

    private fun model(
        cells: Map<Int, TangoCell> = emptyMap(),
        givens: Set<Int> = emptySet(),
        rejection: TangoRendererModel.RejectedMove? = null,
        markers: List<TangoRendererModel.MarkerRender> = emptyList(),
    ) = TangoRendererModel(
        n = 6,
        cells = (0 until 36).map { idx ->
            TangoRendererModel.CellRender(value = cells[idx], isGiven = idx in givens)
        },
        markers = markers,
        rejection = rejection,
        status = TangoRendererModel.Status.PLAYING,
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
        val m = model(
            cells = mapOf(0 to TangoCell.SUN, 1 to TangoCell.SUN, 2 to TangoCell.SUN),
            rejection = TangoRendererModel.RejectedMove(2, TangoVerdict.Reason.ThreeInARow),
        )
        assertEquals(
            "violates: Three suns in a row.",
            TangoSemantics.cellStateDescription(m, 2),
        )
        // Offending cells only — untouched cells stay plain.
        assertEquals("empty", TangoSemantics.cellStateDescription(m, 10))
    }

    // ---- §6 violation copy, exact ----

    @Test
    fun `three in a row copy names the axis and value`() {
        fun sunRowRejected() = model(
            cells = mapOf(6 to TangoCell.SUN, 7 to TangoCell.SUN, 8 to TangoCell.SUN),
            rejection = TangoRendererModel.RejectedMove(8, TangoVerdict.Reason.ThreeInARow),
        )
        assertEquals("Three suns in a row.", TangoSemantics.violationPhrase(sunRowRejected(), 8))
        val moonColumn = model(
            cells = mapOf(3 to TangoCell.MOON, 9 to TangoCell.MOON, 15 to TangoCell.MOON),
            rejection = TangoRendererModel.RejectedMove(15, TangoVerdict.Reason.ThreeInARow),
        )
        assertEquals("Three moons in a column.", TangoSemantics.violationPhrase(moonColumn, 15))
    }

    @Test
    fun `count exceeded copy names the line and the cap`() {
        val cells = (0 until 6).associateWith { TangoCell.SUN }
        val m = model(
            cells = cells,
            rejection = TangoRendererModel.RejectedMove(4, TangoVerdict.Reason.CountExceeded),
        )
        assertEquals("Row 1 has 6 suns — a row holds 3.", TangoSemantics.violationPhrase(m, 4))
        val columnCells = (0 until 6).associate { it * 6 to TangoCell.SUN }
        val columnModel = model(
            cells = columnCells,
            rejection = TangoRendererModel.RejectedMove(18, TangoVerdict.Reason.CountExceeded),
        )
        assertEquals("Column 1 has 6 suns — a column holds 3.", TangoSemantics.violationPhrase(columnModel, 18))
    }

    @Test
    fun `marker conflict copy names the symbol and requirement`() {
        val same = model(
            markers = listOf(TangoRendererModel.MarkerRender(0, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME)),
            rejection = TangoRendererModel.RejectedMove(0, TangoVerdict.Reason.MarkerConflict),
        )
        assertEquals("= pair mismatch — these cells must match.", TangoSemantics.violationPhrase(same, 0))
        assertEquals("= pair mismatch — these cells must match.", TangoSemantics.violationPhrase(same, 1))
        val opposite = model(
            markers = listOf(TangoRendererModel.MarkerRender(0, TangoEdgeSide.DOWN, TangoMarkerKind.OPPOSITE)),
            rejection = TangoRendererModel.RejectedMove(0, TangoVerdict.Reason.MarkerConflict),
        )
        assertEquals("× pair mismatch — these cells must differ.", TangoSemantics.violationPhrase(opposite, 6))
    }

    @Test
    fun `cells without the rejection carry no phrase`() {
        val m = model(
            cells = mapOf(0 to TangoCell.SUN),
            rejection = TangoRendererModel.RejectedMove(0, TangoVerdict.Reason.ThreeInARow),
        )
        assertNull(TangoSemantics.violationPhrase(m, 5))
        assertNull(TangoSemantics.violationPhrase(model(), 3))
    }

    // ---- §9 marker phrasing on affected cells ----

    @Test
    fun `marker phrases append to affected cells only`() {
        val m = model(
            markers = listOf(
                TangoRendererModel.MarkerRender(3, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME),
                TangoRendererModel.MarkerRender(8, TangoEdgeSide.DOWN, TangoMarkerKind.OPPOSITE),
            ),
        )
        assertEquals(
            listOf("same as the cell to the right"),
            TangoSemantics.markerPhrases(m, 3),
        )
        assertEquals(
            listOf("same as the cell to the right"),
            TangoSemantics.markerPhrases(m, 4),
        )
        assertEquals(
            listOf("opposite to the cell below"),
            TangoSemantics.markerPhrases(m, 8),
        )
        assertEquals(
            listOf("opposite to the cell below"),
            TangoSemantics.markerPhrases(m, 14),
        )
        assertTrue(TangoSemantics.markerPhrases(m, 20).isEmpty())
    }

    // ---- §9 count rails ----

    @Test
    fun `count rail descriptions read placed of total`() {
        val m = model(cells = mapOf(0 to TangoCell.SUN, 1 to TangoCell.SUN))
        assertEquals("Row 1 has 2 of 3 suns", TangoSemantics.rowCountDescription(0, 2, 3))
        assertEquals("Column 2 has 0 of 3 suns", TangoSemantics.columnCountDescription(1, 0, 3))
        assertEquals(2, TangoSemantics.sunsInRow(m, 0))
        assertEquals(0, TangoSemantics.sunsInColumn(m, 5))
    }
}
