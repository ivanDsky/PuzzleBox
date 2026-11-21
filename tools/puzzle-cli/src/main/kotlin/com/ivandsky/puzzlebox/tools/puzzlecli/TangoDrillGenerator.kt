package com.ivandsky.puzzlebox.tools.puzzlecli

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarker
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.codec.TangoCodec
import com.ivandsky.puzzlebox.games.tango.engine.TangoUniqueness
import kotlin.random.Random

/**
 * Seeded random 6×6 Tango boards, the size of the official daily: a random valid solution by backtracking, `=` and `×`
 * markers read off it on a share of the interior edges, then the givens removed in shuffled order for as long as the
 * board keeps exactly one solution. The engine's own board generator is internal to `games:tango`, so this one follows
 * its recipe on the public types and [TangoUniqueness].
 */
object TangoDrillGenerator : DrillGenerator {

    private const val N = 6
    private val variant = VariantId("tango/n$N")

    /** The least and the most a candidate's markers cover: each interior edge carries one with a probability drawn in between. */
    private const val MIN_MARKER_SHARE = 0.04
    private const val MAX_MARKER_SHARE = 0.16

    override fun next(random: Random): Puzzle? {
        val solution = solution(random)
        val share = MIN_MARKER_SHARE + random.nextDouble() * (MAX_MARKER_SHARE - MIN_MARKER_SHARE)
        val markers = markers(solution, share, random)
        val givens = solution.withIndex().associate { (idx, cell) -> idx to cell }.toMutableMap()
        for (idx in (0 until N * N).shuffled(random)) {
            val cell = givens.remove(idx) ?: continue
            if (TangoUniqueness.countSolutions(TangoPuzzle(variant, N, givens, markers), limit = 2) != 1) givens[idx] = cell
        }
        val encoded = TangoCodec.encode(TangoPuzzle(variant, N, givens.toMap(), markers))
        return (TangoDefinition.decode(encoded) as? DecodeResult.Ok)?.value
    }

    /** A random valid full grid: row-major fill, each cell's two values tried in random order. */
    private fun solution(random: Random): List<TangoCell> {
        val grid = arrayOfNulls<TangoCell>(N * N)
        fun fits(idx: Int): Boolean {
            val row = idx / N
            val col = idx % N
            val value = grid[idx]
            if (col >= 2 && grid[idx - 1] == value && grid[idx - 2] == value) return false
            if (row >= 2 && grid[idx - N] == value && grid[idx - 2 * N] == value) return false
            val inRow = (0..col).count { grid[row * N + it] == value }
            val inCol = (0..row).count { grid[it * N + col] == value }
            return inRow <= N / 2 && inCol <= N / 2
        }
        fun fill(idx: Int): Boolean {
            if (idx == N * N) return true
            val first = if (random.nextBoolean()) TangoCell.SUN else TangoCell.MOON
            for (value in listOf(first, first.other)) {
                grid[idx] = value
                if (fits(idx) && fill(idx + 1)) return true
            }
            grid[idx] = null
            return false
        }
        check(fill(0)) { "no $N×$N Tango grid" }
        return grid.map { it!! }
    }

    /** Each interior edge carries a marker with probability [share], its kind read off [solution]. */
    private fun markers(solution: List<TangoCell>, share: Double, random: Random): List<TangoMarker> = buildList {
        for (idx in 0 until N * N) {
            for (side in TangoEdgeSide.entries) {
                val next = when (side) {
                    TangoEdgeSide.RIGHT -> if (idx % N < N - 1) idx + 1 else continue
                    TangoEdgeSide.DOWN -> if (idx / N < N - 1) idx + N else continue
                }
                if (random.nextDouble() >= share) continue
                val kind = if (solution[idx] == solution[next]) TangoMarkerKind.SAME else TangoMarkerKind.OPPOSITE
                add(TangoMarker(idx, side, kind))
            }
        }
    }
}
