package com.ivandsky.puzzlebox.games.tango.engine

import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarker
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import java.util.Random

/**
 * Seeded random Tango boards, a port of the spec §5 measurement bench
 * (`.scratch/editor/specs/M4-tango-sizes.md`): a random valid solution, `=`/`×` markers read
 * off it on a share of the edges, then givens removed in shuffled order while the board stays
 * unique. Removing one more given from that minimal board leaves it Multiple.
 *
 * Uniqueness is asked of [TangoUniqueness.countSolutions], whose answers the corpus pins, so a
 * seed always yields the same boards.
 */
internal object TangoBoardGenerator {

    /** A minimal unique board and a copy with one of its givens removed. */
    data class Boards(val minimal: TangoPuzzle, val multiple: TangoPuzzle)

    const val MARKER_SHARE: Double = 0.08

    /** 10–29 holds the 10×10 seeds the plain solver found hardest after 26: 28 (2.4 M nodes), 15, 17 and 29. */
    val CORPUS_SEEDS: LongRange = 10L..29L

    /** No givens; the bottom-right 2×2 markers `a×b`, `a×c`, `b×d`, `c=d` cannot all hold, so it has no solution. */
    fun parityTrap(n: Int): TangoPuzzle {
        val a = (n - 2) * n + (n - 2)
        return TangoPuzzle(
            VariantId("tango/n$n"), n, emptyMap(),
            listOf(
                TangoMarker(a, TangoEdgeSide.RIGHT, TangoMarkerKind.OPPOSITE),
                TangoMarker(a, TangoEdgeSide.DOWN, TangoMarkerKind.OPPOSITE),
                TangoMarker(a + 1, TangoEdgeSide.DOWN, TangoMarkerKind.OPPOSITE),
                TangoMarker(a + n, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME),
            ),
        )
    }

    fun boards(n: Int, seed: Long): Boards {
        val rnd = Random(seed)
        val solution = solution(n, rnd)
        val markers = markers(n, solution, rnd)
        val givens = solution.withIndex().associate { (idx, cell) -> idx to cell }.toMutableMap()
        val variant = VariantId("tango/n$n")
        for (idx in (0 until n * n).shuffled(rnd)) {
            val cell = givens.remove(idx) ?: continue
            if (TangoUniqueness.countSolutions(TangoPuzzle(variant, n, givens, markers), limit = 2) != 1) givens[idx] = cell
        }
        val minimal = TangoPuzzle(variant, n, givens.toMap(), markers)
        val dropped = givens.keys.sorted()[rnd.nextInt(givens.size)]
        return Boards(minimal, minimal.copy(givens = givens - dropped))
    }

    /** A uniformly seeded valid full grid: row-major fill, each cell's two values tried in random order. */
    private fun solution(n: Int, rnd: Random): List<TangoCell> {
        val grid = arrayOfNulls<TangoCell>(n * n)
        fun fits(idx: Int): Boolean {
            val row = idx / n
            val col = idx % n
            val value = grid[idx]
            if (col >= 2 && grid[idx - 1] == value && grid[idx - 2] == value) return false
            if (row >= 2 && grid[idx - n] == value && grid[idx - 2 * n] == value) return false
            val inRow = (0..col).count { grid[row * n + it] == value }
            val inCol = (0..row).count { grid[it * n + col] == value }
            return inRow <= n / 2 && inCol <= n / 2
        }
        fun fill(idx: Int): Boolean {
            if (idx == n * n) return true
            val first = if (rnd.nextBoolean()) TangoCell.SUN else TangoCell.MOON
            for (value in listOf(first, first.other())) {
                grid[idx] = value
                if (fits(idx) && fill(idx + 1)) return true
            }
            grid[idx] = null
            return false
        }
        check(fill(0)) { "no $n×$n Tango grid" }
        return grid.map { it!! }
    }

    /** Each interior edge carries a marker with probability [MARKER_SHARE], its kind read off [solution]. */
    private fun markers(n: Int, solution: List<TangoCell>, rnd: Random): List<TangoMarker> = buildList {
        for (idx in 0 until n * n) {
            for (side in TangoEdgeSide.entries) {
                val neighbor = when (side) {
                    TangoEdgeSide.RIGHT -> if (idx % n < n - 1) idx + 1 else continue
                    TangoEdgeSide.DOWN -> if (idx / n < n - 1) idx + n else continue
                }
                if (rnd.nextDouble() >= MARKER_SHARE) continue
                val kind = if (solution[idx] == solution[neighbor]) TangoMarkerKind.SAME else TangoMarkerKind.OPPOSITE
                add(TangoMarker(idx, side, kind))
            }
        }
    }

    private fun TangoCell.other(): TangoCell = if (this == TangoCell.SUN) TangoCell.MOON else TangoCell.SUN
}
