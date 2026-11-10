package com.ivandsky.puzzlebox.games.queens.engine.hints.ladder

import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.engine.hints.HintReason

/**
 * D8: a row, column or region still missing queens, with at least 2 open cells, would be
 * left no room by a queen on any open cell outside it that rules out all of them, so each
 * such cell is excluded. One exclusion per unit, naming it; a unit down to one open cell is
 * [SinglesRung]'s. A queen rules out what [QueensCandidates.place] crosses: the cells it
 * touches, and the cells of a row, column or region it fills.
 *
 * Order: ROW → COLUMN → REGION, then lowest unit index.
 */
object SqueezeRung : QueensRung {
    override val id: String = "squeeze"
    override val level: Difficulty.Level = Difficulty.Level.Hard
    override val captionKey: String = "queens.hint.rung.squeeze"

    override fun steps(puzzle: QueensPuzzle, candidates: QueensCandidates): Sequence<QueensProof> =
        found(puzzle, candidates).map(Found<QueensDeduction.Exclude>::proof)

    override fun step(puzzle: QueensPuzzle, candidates: QueensCandidates): QueensDeduction? =
        found(puzzle, candidates).firstOrNull()?.deduction

    /**
     * Lazy and already in order, one unit at a time, so [step] stops at the first and builds
     * no evidence. The buffers are shared across units: a unit's scan finishes before the next.
     */
    private fun found(puzzle: QueensPuzzle, candidates: QueensCandidates): Sequence<Found<QueensDeduction.Exclude>> {
        val n = puzzle.size
        val isOpen = BooleanArray(n * n) { candidates.isOpen(it) }
        val queens = HintReason.Kind.entries.associateWith { kind ->
            IntArray(n).also { count -> for (queen in candidates.queens) count[QueensUnits.indexOf(puzzle, kind, queen)]++ }
        }
        val open = IntArray(n * n)
        return HintReason.Kind.entries.asSequence()
            .flatMap { kind -> (0 until n).asSequence().map { kind to it } }
            .mapNotNull { (kind, unit) ->
                if (queens.getValue(kind)[unit] >= puzzle.queensPerUnit) return@mapNotNull null
                var size = 0
                for (cell in QueensUnits.cells(puzzle, kind)[unit]) if (isOpen[cell]) open[size++] = cell
                if (size < 2) return@mapNotNull null
                val squeezing = (0 until n * n).filter { x ->
                    isOpen[x] && QueensUnits.indexOf(puzzle, kind, x) != unit && (0 until size).all { rulesOut(puzzle, queens, x, open[it]) }
                }.toSet()
                if (squeezing.isEmpty()) return@mapNotNull null
                val reason = HintReason(kind, unit, this)
                Found(QueensDeduction.Exclude(this, reason, squeezing)) {
                    QueensUnits.cells(puzzle, kind)[unit].filter { isOpen[it] }.toSet()
                }
            }
    }

    /** A queen on [x] crosses [cell]; on one-queen boards this is [QueensAttack.attacks]. */
    private fun rulesOut(puzzle: QueensPuzzle, queens: Map<HintReason.Kind, IntArray>, x: Int, cell: Int): Boolean {
        val n = puzzle.size
        fun fills(kind: HintReason.Kind): Boolean {
            val unit = QueensUnits.indexOf(puzzle, kind, x)
            return unit == QueensUnits.indexOf(puzzle, kind, cell) && queens.getValue(kind)[unit] + 1 >= puzzle.queensPerUnit
        }
        return QueensAttack.touches(x, cell, n) || fills(HintReason.Kind.ROW) || fills(HintReason.Kind.COLUMN) || fills(HintReason.Kind.REGION)
    }
}
