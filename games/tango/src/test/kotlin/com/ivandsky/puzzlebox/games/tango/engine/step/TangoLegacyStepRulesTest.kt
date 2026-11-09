package com.ivandsky.puzzlebox.games.tango.engine.step

import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoCell.MOON
import com.ivandsky.puzzlebox.games.tango.TangoCell.SUN
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarker
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.engine.TangoHint
import com.ivandsky.puzzlebox.games.tango.engine.TangoTripleRule
import org.junit.Assert.assertEquals
import org.junit.Test

class TangoLegacyStepRulesTest {

    private fun board(vararg markers: TangoMarker): TangoPuzzle =
        TangoPuzzle(VariantId("tango/n6"), 6, emptyMap(), markers.toList())

    private fun grid(vararg cells: Pair<Int, TangoCell>): List<TangoCell?> =
        MutableList<TangoCell?>(36) { null }.apply { cells.forEach { (idx, v) -> this[idx] = v } }

    private fun deduction(rung: TangoStepRung, idx: Int, value: TangoCell, evidence: Set<Int>, row: Int? = null, col: Int? = null, options: Int? = null) =
        TangoStep.Deduction(rung, idx, value, evidence, row, col, options)

    @Test
    fun `a marker binding is a sign step lighting both marker cells`() {
        val puzzle = board(TangoMarker(7, TangoEdgeSide.RIGHT, TangoMarkerKind.OPPOSITE))

        val steps = TangoLegacyStepRules.sign.forced(puzzle, grid(7 to SUN))

        assertEquals(listOf(deduction(TangoStepRung.SIGN, 8, MOON, setOf(7, 8))), steps)
    }

    @Test
    fun `cells beyond both ends of an equal pair are pair steps`() {
        val g = grid(2 to SUN, 3 to SUN)

        val steps = TangoLegacyStepRules.pair.forced(board(), g)

        assertEquals(
            listOf(deduction(TangoStepRung.PAIR, 1, MOON, setOf(2, 3)), deduction(TangoStepRung.PAIR, 4, MOON, setOf(2, 3))),
            steps,
        )
        assertEquals(emptyList<TangoStep.Deduction>(), TangoLegacyStepRules.sandwich.forced(board(), g))
    }

    @Test
    fun `the gap between two equal cells is a sandwich step`() {
        val g = grid(6 to MOON, 18 to MOON)

        val steps = TangoLegacyStepRules.sandwich.forced(board(), g)

        assertEquals(listOf(deduction(TangoStepRung.SANDWICH, 12, SUN, setOf(6, 18))), steps)
        assertEquals(emptyList<TangoStep.Deduction>(), TangoLegacyStepRules.pair.forced(board(), g))
    }

    @Test
    fun `one legacy three in a row splits by where the target sits`() {
        val trailing = TangoHint.Forced(2, MOON, TangoHint.Why.ThreeInARow(0, 1))
        val leading = TangoHint.Forced(0, MOON, TangoHint.Why.ThreeInARow(1, 2))
        val between = TangoHint.Forced(1, MOON, TangoHint.Why.ThreeInARow(0, 2))

        val rungs = listOf(trailing, leading, between).map { TangoLegacyStepRules.deduction(board(), grid(), it).rung }

        assertEquals(listOf(TangoStepRung.PAIR, TangoStepRung.PAIR, TangoStepRung.SANDWICH), rungs)
    }

    @Test
    fun `pair and sandwich together cover every legacy three in a row`() {
        val g = grid(0 to SUN, 2 to SUN, 9 to MOON, 15 to MOON, 30 to SUN, 31 to SUN)

        val split = (TangoLegacyStepRules.pair.forced(board(), g) + TangoLegacyStepRules.sandwich.forced(board(), g)).map { it.idx to it.value }

        assertEquals(TangoTripleRule.forced(board(), g).map { it.idx to it.value }.toSet(), split.toSet())
        assertEquals(TangoTripleRule.forced(board(), g).size, split.size)
    }

    @Test
    fun `a full row is a full line step lighting the cells of the full value`() {
        val g = grid(0 to SUN, 2 to SUN, 4 to SUN, 1 to MOON)

        val steps = TangoLegacyStepRules.fullLine.forced(board(), g).filter { it.row == 0 }

        assertEquals(
            listOf(3, 5).map { deduction(TangoStepRung.FULL_LINE, it, MOON, setOf(0, 2, 4), row = 0) },
            steps,
        )
    }

    @Test
    fun `a full column names the column`() {
        val g = grid(1 to MOON, 13 to MOON, 25 to MOON)

        val steps = TangoLegacyStepRules.fullLine.forced(board(), g)

        assertEquals(
            listOf(7, 19, 31).map { deduction(TangoStepRung.FULL_LINE, it, SUN, setOf(1, 13, 25), col = 1) },
            steps,
        )
    }

    @Test
    fun `line fills become line options carrying the fill count and the line's filled cells`() {
        // Row 0 `S S _ _ _ _` has two legal fills, SSMSMM and SSMMSM; both put a moon on cells 2 and 5.
        val steps = TangoLegacyStepRules.lineOptions.forced(board(), grid(0 to SUN, 1 to SUN))

        assertEquals(
            listOf(2, 5).map { deduction(TangoStepRung.LINE_OPTIONS, it, MOON, setOf(0, 1), row = 0, options = 2) },
            steps,
        )
    }
}
