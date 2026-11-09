package com.ivandsky.puzzlebox.games.tango.engine

import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarker
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import org.junit.Assert.assertEquals
import java.util.Random
import org.junit.Test

/**
 * [TangoPropagation] re-implements the three rule classes over arrays; this pins it against
 * [TangoTripleRule], [TangoCountRule] and [TangoMarkerRule] on random rule-clean partial grids.
 */
class TangoPropagationRulesTest {

    @Test
    fun `place breaks a constraint exactly when the rule classes see a violation`() {
        for (case in 0 until CASES) {
            val puzzle = cleanPartial(Random(case.toLong()))
            for (idx in 0 until puzzle.cellCount) {
                if (idx in puzzle.givens) continue
                for (value in TangoCell.entries) {
                    val grid = List(puzzle.cellCount) { puzzle.givens[it] }.toMutableList().apply { this[idx] = value }

                    assertEquals("case $case cell $idx $value", !violates(puzzle, grid), TangoPropagation(puzzle).place(idx, value))
                }
            }
        }
    }

    @Test
    fun `propagate reaches the fixpoint of the rule deductions and fails where they contradict`() {
        for (case in 0 until CASES) {
            val puzzle = cleanPartial(Random(case.toLong()))
            val state = TangoPropagation(puzzle)
            val expected = deduceAll(puzzle)

            val settled = state.propagate()

            assertEquals("case $case settled", expected != null, settled)
            if (expected != null) assertEquals("case $case grid", expected, state.grid.toList())
        }
    }

    /** Applies the rules' first deductions until none is left; null when one breaks a rule. */
    private fun deduceAll(puzzle: TangoPuzzle): List<TangoCell?>? {
        val grid = MutableList(puzzle.cellCount) { puzzle.givens[it] }
        while (true) {
            val forced = TangoTripleRule.deduction(puzzle, grid)
                ?: TangoCountRule.deduction(puzzle, grid)
                ?: TangoMarkerRule.deduction(puzzle, grid)
                ?: return grid
            grid[forced.idx] = forced.value
            if (violates(puzzle, grid)) return null
        }
    }

    private fun violates(puzzle: TangoPuzzle, grid: List<TangoCell?>): Boolean =
        TangoTripleRule.violation(puzzle, grid) != null ||
            TangoCountRule.violation(puzzle, grid) != null ||
            TangoMarkerRule.violation(puzzle, grid) != null

    /**
     * An even-size board with markers on some edges, never closing a cycle (an odd `=`/`×` cycle
     * is refused up front by a parity check the rule classes have no counterpart for), and givens
     * dropped in shuffled order, each kept only while the rule classes see no violation. Its
     * givens may not agree on a full solution: [TangoPropagation.propagate] has to find that out.
     */
    private fun cleanPartial(rnd: Random): TangoPuzzle {
        val n = SIZES[rnd.nextInt(SIZES.size)]
        val root = IntArray(n * n) { it }
        fun find(x: Int): Int = if (root[x] == x) x else find(root[x]).also { root[x] = it }
        val markers = buildList {
            for (cell in 0 until n * n) {
                for (side in TangoEdgeSide.entries) {
                    val other = if (side == TangoEdgeSide.RIGHT) cell + 1 else cell + n
                    val fits = if (side == TangoEdgeSide.RIGHT) cell % n < n - 1 else cell / n < n - 1
                    if (!fits || rnd.nextDouble() >= MARKER_SHARE || find(cell) == find(other)) continue
                    root[find(cell)] = find(other)
                    add(TangoMarker(cell, side, kind(rnd)))
                }
            }
        }
        val empty = TangoPuzzle(VariantId("tango/n$n"), n, emptyMap(), markers)
        val grid = MutableList<TangoCell?>(n * n) { null }
        val wanted = rnd.nextInt(n * n / 2) + 1
        for (idx in (0 until n * n).shuffled(rnd).take(wanted)) {
            grid[idx] = if (rnd.nextBoolean()) TangoCell.SUN else TangoCell.MOON
            if (violates(empty, grid)) grid[idx] = null
        }
        val givens = grid.withIndex().filter { it.value != null }.associate { it.index to it.value!! }
        return empty.copy(givens = givens)
    }

    private fun kind(rnd: Random): TangoMarkerKind = if (rnd.nextBoolean()) TangoMarkerKind.SAME else TangoMarkerKind.OPPOSITE

    private companion object {
        const val CASES = 400
        const val MARKER_SHARE = 0.15
        val SIZES = intArrayOf(4, 6, 8, 10)
    }
}
