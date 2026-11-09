package com.ivandsky.puzzlebox.games.queens.engine.hints.ladder

import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.engine.hints.HintReason

/**
 * D7: a band of k unsolved lines (2 ≤ k ≤ [MAX_BAND]) and the unsolved regions whose open
 * cells lie wholly inside it. When those regions miss one queen fewer than the band, they take
 * all but one of the band's queens, so the band's open cells outside them (the leftover set S)
 * hold exactly one. A queen on any other open cell that would rule out every cell of S is then
 * impossible, so that cell is excluded. The mirror: a band of k regions and the lines wholly
 * inside it. Rows and columns are tried both ways. With one queen per unit this is "k lines
 * hold k queens, k−1 regions inside".
 *
 * The evidence is the band's open cells (the inside units' open cells plus S); the reason names
 * the band's lowest unit. Order: smallest k, then lowest excluded cell, then lowest unit.
 */
object LeftoverQueenRung : QueensRung {
    override val id: String = "leftover-queen"
    override val level: Difficulty.Level = Difficulty.Level.Hard
    override val captionKey: String = "queens.hint.rung.leftover-queen"

    /** Bounds the band search (lab `LEFT_MAXK`) so the rung stays cheap on 11×11. */
    private const val MAX_BAND = 4

    override fun steps(puzzle: QueensPuzzle, candidates: QueensCandidates): Sequence<QueensProof> =
        found(puzzle, candidates).sortedWith(order).asSequence().map { QueensProof(it.deduction, it.evidence()) }

    override fun step(puzzle: QueensPuzzle, candidates: QueensCandidates): QueensDeduction? =
        found(puzzle, candidates).minWithOrNull(order)?.deduction

    /** A deduction with its evidence still unbuilt, so [step] builds none for the losing candidates. */
    private class Found(val k: Int, val deduction: QueensDeduction.Exclude, val evidence: () -> Set<Int>)

    private val order: Comparator<Found> = compareBy(
        { it.k },
        { it.deduction.cells.min() },
        { it.deduction.unit.kind },
        { it.deduction.unit.unit },
    )

    /** One family of units: its cells and missing queens per index, a cell's index, and its kind. */
    private class Units(val cells: List<List<Int>>, val missing: List<Int>, val of: (Int) -> Int, val kind: HintReason.Kind)

    private fun found(puzzle: QueensPuzzle, candidates: QueensCandidates): List<Found> {
        val n = puzzle.size
        fun units(cells: List<List<Int>>, of: (Int) -> Int, kind: HintReason.Kind) =
            Units(cells, cells.map { unit -> puzzle.queensPerUnit - unit.count { it in candidates.queens } }, of, kind)
        val rows = units(List(n) { row -> List(n) { row * n + it } }, { it / n }, HintReason.Kind.ROW)
        val cols = units(List(n) { col -> List(n) { it * n + col } }, { it % n }, HintReason.Kind.COLUMN)
        val regions = units(List(n) { region -> puzzle.regions.indices.filter { puzzle.regions[it] == region } }, puzzle.regions::get, HintReason.Kind.REGION)
        val board = Board(puzzle, candidates, rows, cols, regions)
        return listOf(rows, cols).flatMap { lines -> board.leftovers(lines, regions) + board.leftovers(regions, lines) }
    }

    private class Board(
        private val puzzle: QueensPuzzle,
        private val candidates: QueensCandidates,
        private val rows: Units,
        private val cols: Units,
        private val regions: Units,
    ) {
        private val n = puzzle.size

        /**
         * A queen on [x] rules out [s] when they touch, or share a unit that queen would fill. With
         * one queen per unit that is [QueensAttack.attacks]; on an N=2 board a shared unit still
         * missing two queens leaves room for both.
         */
        fun rulesOut(x: Int, s: Int): Boolean =
            QueensAttack.touches(x, s, n) ||
                (x / n == s / n && rows.missing[x / n] == 1) ||
                (x % n == s % n && cols.missing[x % n] == 1) ||
                (puzzle.regions[x] == puzzle.regions[s] && regions.missing[puzzle.regions[x]] == 1)

        /** Every exclusion from a band of [bands] units with [insides] units wholly inside it. */
        fun leftovers(bands: Units, insides: Units): List<Found> {
            val unsolved = bands.cells.indices.filter { bands.missing[it] > 0 }
            // Unsolved inside candidates with at least one open cell, and the bitmask of band units those cells reach.
            val insideUnits = ArrayList<Int>()
            val insideReach = ArrayList<Int>()
            for (unit in insides.cells.indices) {
                val open = insides.cells[unit].filter(candidates::isOpen)
                if (insides.missing[unit] == 0 || open.isEmpty()) continue
                insideUnits += unit
                insideReach += open.fold(0) { bits, cell -> bits or (1 shl bands.of(cell)) }
            }
            val leftover = IntArray(n * n)
            val inLeftover = BooleanArray(n * n)
            val found = ArrayList<Found>()
            for (mask in 1 until (1 shl unsolved.size)) {
                val k = Integer.bitCount(mask)
                if (k < 2 || k > MAX_BAND) continue
                var bandBits = 0
                var bandNeed = 0
                for (i in unsolved.indices) if (mask and (1 shl i) != 0) {
                    bandBits = bandBits or (1 shl unsolved[i])
                    bandNeed += bands.missing[unsolved[i]]
                }
                var insideBits = 0
                var insideNeed = 0
                for (i in insideUnits.indices) if (insideReach[i] and bandBits.inv() == 0) {
                    insideBits = insideBits or (1 shl insideUnits[i])
                    insideNeed += insides.missing[insideUnits[i]]
                }
                if (insideNeed != bandNeed - 1) continue
                var size = 0
                for (cell in 0 until n * n) {
                    if (candidates.isOpen(cell) && bandBits and (1 shl bands.of(cell)) != 0 && insideBits and (1 shl insides.of(cell)) == 0) {
                        leftover[size++] = cell
                        inLeftover[cell] = true
                    }
                }
                if (size == 0) continue
                val cells = (0 until n * n).filter { x ->
                    candidates.isOpen(x) && !inLeftover[x] && (0 until size).all { rulesOut(x, leftover[it]) }
                }.toSet()
                for (i in 0 until size) inLeftover[leftover[i]] = false
                if (cells.isEmpty()) continue
                val unit = HintReason(bands.kind, Integer.numberOfTrailingZeros(bandBits), LeftoverQueenRung)
                found += Found(k, QueensDeduction.Exclude(LeftoverQueenRung, unit, cells)) {
                    (0 until n * n).filter { candidates.isOpen(it) && bandBits and (1 shl bands.of(it)) != 0 }.toSet()
                }
            }
            return found
        }
    }
}
