package com.ivandsky.puzzlebox.games.tango

import com.ivandsky.puzzlebox.games.tango.engine.TangoCountRule
import com.ivandsky.puzzlebox.games.tango.engine.TangoHint
import com.ivandsky.puzzlebox.games.tango.engine.TangoMarkerRule
import com.ivandsky.puzzlebox.games.tango.engine.TangoTripleRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Each constraint class is a self-contained unit: violation detection and its deduction. */
class TangoConstraintsTest {

    private val variant = TangoTestSupport.variant

    private fun puzzle(
        n: Int = 6,
        givens: Map<Int, TangoCell> = emptyMap(),
        markers: List<TangoMarker> = emptyList(),
    ) = TangoPuzzle(variant, n, givens, markers)

    private fun grid(n: Int, vararg rows: String): List<TangoCell?> =
        rows.flatMap { r -> r.map { c -> when (c) { 'S' -> TangoCell.SUN; 'M' -> TangoCell.MOON; else -> null } } }

    // -- marker rule ----------------------------------------------------------

    @Test
    fun `a satisfied same marker is no violation`() {
        val p = puzzle(markers = listOf(TangoMarker(0, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME)))
        assertNull(TangoMarkerRule.violation(p, grid(6, "SS----", "------", "------", "------", "------", "------")))
    }

    @Test
    fun `a same marker with opposite ends is a violation`() {
        val p = puzzle(markers = listOf(TangoMarker(0, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME)))
        assertEquals(0, TangoMarkerRule.violation(p, grid(6, "SM----", "------", "------", "------", "------", "------"))?.cell)
    }

    @Test
    fun `an opposite marker with equal ends is a violation`() {
        val p = puzzle(markers = listOf(TangoMarker(7, TangoEdgeSide.DOWN, TangoMarkerKind.OPPOSITE)))
        assertEquals(7, TangoMarkerRule.violation(p, grid(6, "------", "-S----", "-S----", "------", "------", "------"))?.cell)
    }

    @Test
    fun `a half-filled marker edge is never a violation`() {
        val p = puzzle(markers = listOf(TangoMarker(0, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME)))
        assertNull(TangoMarkerRule.violation(p, grid(6, "S-----", "------", "------", "------", "------", "------")))
    }

    @Test
    fun `a marker chain propagates across a whole run of edges`() {
        // Two chained `=` edges: cell 1 filled forces both 0 and 2, whichever end is empty first.
        val p = puzzle(markers = listOf(
            TangoMarker(0, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME),
            TangoMarker(1, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME),
        ))
        val hint = TangoMarkerRule.deduction(p, grid(6, "-S----", "------", "------", "------", "------", "------"))
        assertEquals(TangoCell.SUN, hint?.value)
        assertEquals(0, hint?.idx)
    }

    @Test
    fun `an opposite marker propagates the inverted value`() {
        val p = puzzle(markers = listOf(TangoMarker(0, TangoEdgeSide.RIGHT, TangoMarkerKind.OPPOSITE)))
        val hint = TangoMarkerRule.deduction(p, grid(6, "S-----", "------", "------", "------", "------", "------"))
        assertEquals(1, hint?.idx)
        assertEquals(TangoCell.MOON, hint?.value)
        assertEquals(TangoMarkerKind.OPPOSITE, (hint?.why as com.ivandsky.puzzlebox.games.tango.engine.TangoHint.Why.MarkerBinding).kind)
    }

    @Test
    fun `a fully empty marker edge yields no deduction`() {
        val p = puzzle(markers = listOf(TangoMarker(0, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME)))
        assertNull(TangoMarkerRule.deduction(p, grid(6, "------", "------", "------", "------", "------", "------")))
    }

    // -- triple rule ----------------------------------------------------------

    @Test
    fun `a full alternating row has no triple`() {
        val p = puzzle()
        assertNull(TangoTripleRule.violation(p, grid(6, "SSMSMM", "SSMSMM", "MMSMSS", "SSMMSM", "MMSSMS", "MMSMSS")))
    }

    @Test
    fun `a run of three in a row reports its third cell`() {
        val p = puzzle()
        assertEquals(2, TangoTripleRule.violation(p, grid(6, "SSS---", "------", "------", "------", "------", "------")))
    }

    @Test
    fun `a run of three in a column reports its third cell`() {
        val p = puzzle()
        assertEquals(13, TangoTripleRule.violation(p, grid(6, "-S----", "-S----", "-S----", "------", "------", "------")))
    }

    @Test
    fun `two identical cells force the continuing cell to the other value`() {
        val p = puzzle()
        val hint = TangoTripleRule.deduction(p, grid(6, "SS----", "------", "------", "------", "------", "------"))
        assertEquals(2, hint?.idx)
        assertEquals(TangoCell.MOON, hint?.value)
    }

    @Test
    fun `two identical cells force the leading empty cell too`() {
        val p = puzzle()
        val hint = TangoTripleRule.deduction(p, grid(6, "-SS---", "------", "------", "------", "------", "------"))
        assertEquals(0, hint?.idx)
        assertEquals(TangoCell.MOON, hint?.value)
    }

    @Test
    fun `a gap sandwiched between identical cells forces the other value`() {
        val p = puzzle()
        val hint = TangoTripleRule.deduction(p, grid(6, "S-S---", "------", "------", "------", "------", "------"))
        assertEquals(1, hint?.idx)
        assertEquals(TangoCell.MOON, hint?.value)
        assertEquals(TangoHint.Why.ThreeInARow(0, 2), hint?.why)
    }

    @Test
    fun `a sandwich of opposite ends forces nothing`() {
        val p = puzzle()
        assertNull(TangoTripleRule.deduction(p, grid(6, "S-M---", "------", "------", "------", "------", "------")))
    }

    // -- count rule -----------------------------------------------------------

    @Test
    fun `a line at exactly half of each value violates nothing`() {
        val p = puzzle()
        assertNull(TangoCountRule.violation(p, grid(6, "SSMSMM", "------", "------", "------", "------", "------")))
    }

    @Test
    fun `a line over half suns is a violation`() {
        val p = puzzle()
        assertEquals(3, TangoCountRule.violation(p, grid(6, "SSSS--", "------", "------", "------", "------", "------")))
    }

    @Test
    fun `a line over half moons is a violation`() {
        val p = puzzle()
        assertEquals(11, TangoCountRule.violation(p, grid(6, "------", "MMM--M", "------", "------", "------", "------")))
    }

    @Test
    fun `a column over half is a violation`() {
        val p = puzzle()
        assertEquals(18, TangoCountRule.violation(p, grid(6, "M-----", "M-----", "M-----", "M-----", "------", "------")))
    }

    @Test
    fun `a line at half suns with a gap left forces a moon`() {
        val p = puzzle()
        val hint = TangoCountRule.deduction(p, grid(6, "SSS---", "------", "------", "------", "------", "------"))
        assertEquals(3, hint?.idx)
        assertEquals(TangoCell.MOON, hint?.value)
        assertEquals(TangoCell.SUN, (hint?.why as com.ivandsky.puzzlebox.games.tango.engine.TangoHint.Why.LineFull).fullOf)
    }

    @Test
    fun `a full line at half of each yields no deduction`() {
        val p = puzzle()
        assertNull(TangoCountRule.deduction(p, grid(6, "SSMSMM", "------", "------", "------", "------", "------")))
    }
}
