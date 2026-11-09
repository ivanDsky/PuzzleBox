package com.ivandsky.puzzlebox.games.queens.engine.hints.step

import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensBoard
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.LongLookAheadRung
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensAttack
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensCandidates
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensDeduction
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensLadder
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensProof
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensRung
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.ShortLookAheadRung
import kotlin.math.abs
import kotlin.math.max

/**
 * The one next stepwise hint on a Queens board: solved, else a wrong entry nearest the last
 * move, else the cheapest [QueensLadder.Stepwise] proof, else a reveal. Pure and bounded: the
 * solution comes from the caller (null when it could not be computed), so nothing here
 * searches. Without a solution only rule breaks are mistakes and nothing is revealed.
 */
object QueensStepper {

    fun next(puzzle: QueensPuzzle, board: QueensBoard, autoCross: Boolean, lastMove: Cell?, solution: Set<Cell>?): QueensStep =
        next(puzzle, board, autoCross, lastMove, solution, QueensLadder.Stepwise)

    internal fun next(
        puzzle: QueensPuzzle,
        board: QueensBoard,
        autoCross: Boolean,
        lastMove: Cell?,
        solution: Set<Cell>?,
        ladder: QueensLadder,
    ): QueensStep {
        val queens = board.queens.mapTo(HashSet(board.queens.size * 2)) { it.idx }
        // A manual mark may sit hidden under a queen; only visible crosses are entries.
        val crosses = board.manual.mapNotNullTo(HashSet(board.manual.size * 2)) { cell -> cell.idx.takeIf { it !in queens } }
        val clash = Clash(puzzle, queens)
        if (queens.size == puzzle.size * puzzle.queensPerUnit && queens.none { clash.attacked(it) != null }) return QueensStep.Solved
        mistake(puzzle, queens, crosses, clash, lastMove, solution)?.let { return it }
        // Every entry left is unaccused (with a solution: correct), so all of them seed the ladder (A5).
        // Auto-cross off stands the queens bare, so D0 lists the crosses the player still owes.
        val seeded = if (autoCross) QueensCandidates.start(puzzle, queens) else QueensCandidates(puzzle.size, queens, emptySet())
        val candidates = seeded.exclude(crosses)
        deduction(puzzle, candidates, ladder, lastMove)?.let { return it }
        if (solution == null) return QueensStep.Unavailable
        return reveal(puzzle, candidates, lastMove, solution) ?: QueensStep.Unavailable
    }

    /** The entry to accuse: nearest [lastMove], a queen before a cross, then lowest cell. Auto marks are never judged (A5). */
    private fun mistake(
        puzzle: QueensPuzzle,
        queens: Set<Int>,
        crosses: Set<Int>,
        clash: Clash,
        lastMove: Cell?,
        solution: Set<Cell>?,
    ): QueensStep.Mistake? {
        val n = puzzle.size
        var best: QueensStep.Mistake? = null
        var bestKey = Long.MAX_VALUE
        fun offer(cell: Int, isCross: Boolean, kind: QueensStep.Mistake.Kind, other: Int?) {
            val key = (chebyshev(n, cell, lastMove).toLong() shl 32) or ((if (isCross) 1L else 0L) shl 31) or cell.toLong()
            if (key < bestKey) {
                bestKey = key
                best = QueensStep.Mistake(Cell(cell), kind, other?.let(::Cell))
            }
        }
        for (queen in queens) {
            val other = clash.attacked(queen)
            when {
                other != null && (solution == null || Cell(queen) !in solution) -> offer(queen, false, QueensStep.Mistake.Kind.RULE_BREAK, other)
                solution != null && Cell(queen) !in solution -> offer(queen, false, QueensStep.Mistake.Kind.OFF_SOLUTION, null)
            }
        }
        if (solution != null) {
            for (cross in crosses) if (Cell(cross) in solution) offer(cross, true, QueensStep.Mistake.Kind.WRONG_CROSS, null)
        }
        return best
    }

    /**
     * The lowest rung with any proof; within it the smallest evidence, then the changed cell
     * nearest [lastMove], then that cell, then the rung's own order (A6). A look-ahead rung
     * offers only its shortest chains.
     */
    private fun deduction(puzzle: QueensPuzzle, candidates: QueensCandidates, ladder: QueensLadder, lastMove: Cell?): QueensStep.Deduction? {
        val n = puzzle.size
        for (rung in ladder.rungs) {
            var best: QueensProof? = null
            var bestKey = Long.MAX_VALUE
            for (proof in candidateProofs(rung, puzzle, candidates)) {
                val near = when (val d = proof.deduction) {
                    is QueensDeduction.Place -> d.cell
                    is QueensDeduction.Exclude -> d.cells.minWith(compareBy({ chebyshev(n, it, lastMove) }, { it }))
                }
                val key = (proof.evidence.size.toLong() shl 40) or (chebyshev(n, near, lastMove).toLong() shl 20) or near.toLong()
                if (key < bestKey) {
                    bestKey = key
                    best = proof
                }
            }
            if (best != null) return best.toStep()
        }
        return null
    }

    /**
     * A shorter chain is the simpler human step, and stopping at the first break depth spares
     * running every chain to its end (Main ruling on 13); other rungs offer all their proofs.
     */
    private fun candidateProofs(rung: QueensRung, puzzle: QueensPuzzle, candidates: QueensCandidates): Sequence<QueensProof> =
        when (rung) {
            is ShortLookAheadRung -> rung.stepsAtFirstBreak(puzzle, candidates).asSequence()
            is LongLookAheadRung -> rung.stepsAtFirstBreak(puzzle, candidates).asSequence()
            else -> rung.steps(puzzle, candidates)
        }

    private fun QueensProof.toStep(): QueensStep.Deduction {
        val result = when (val d = deduction) {
            is QueensDeduction.Place -> QueensStep.Deduction.Result.LastCell(Cell(d.cell))
            is QueensDeduction.Exclude -> QueensStep.Deduction.Result.Cross(d.cells.mapTo(HashSet(d.cells.size * 2), ::Cell))
        }
        return QueensStep.Deduction(deduction.rung, deduction.unit, evidence.mapTo(HashSet(evidence.size * 2), ::Cell), result)
    }

    /**
     * An open cell off the [solution] in the unsolved unit with the fewest open cells, then
     * nearest [lastMove], then lowest cell (A8). Null only when no such cell exists.
     */
    private fun reveal(puzzle: QueensPuzzle, candidates: QueensCandidates, lastMove: Cell?, solution: Set<Cell>): QueensStep.Reveal? {
        val n = puzzle.size
        // Units 0 until n are rows, n until 2n columns, 2n until 3n regions.
        val queensIn = IntArray(3 * n)
        val openIn = IntArray(3 * n)
        for (cell in 0 until n * n) {
            val tally = when {
                cell in candidates.queens -> queensIn
                candidates.isOpen(cell) -> openIn
                else -> continue
            }
            tally[cell / n]++
            tally[n + cell % n]++
            tally[2 * n + puzzle.regions[cell]]++
        }
        var best = -1
        var bestKey = Long.MAX_VALUE
        for (cell in 0 until n * n) {
            if (!candidates.isOpen(cell) || Cell(cell) in solution) continue
            var smallest = Int.MAX_VALUE
            for (unit in intArrayOf(cell / n, n + cell % n, 2 * n + puzzle.regions[cell])) {
                if (queensIn[unit] < puzzle.queensPerUnit) smallest = minOf(smallest, openIn[unit])
            }
            if (smallest == Int.MAX_VALUE) continue
            val key = (smallest.toLong() shl 40) or (chebyshev(n, cell, lastMove).toLong() shl 20) or cell.toLong()
            if (key < bestKey) {
                bestKey = key
                best = cell
            }
        }
        return if (best < 0) null else QueensStep.Reveal(Cell(best), holdsQueen = false)
    }

    /**
     * Which queens attack which: touching, or sharing a row, column or region that holds more
     * than [QueensPuzzle.queensPerUnit] queens.
     */
    private class Clash(private val puzzle: QueensPuzzle, private val queens: Set<Int>) {
        private val n = puzzle.size
        private val inRow = IntArray(n)
        private val inColumn = IntArray(n)
        private val inRegion = IntArray(n)

        init {
            for (q in queens) {
                inRow[q / n]++
                inColumn[q % n]++
                inRegion[puzzle.regions[q]]++
            }
        }

        /** The queen nearest [queen] that it attacks, lowest cell on ties, or null. */
        fun attacked(queen: Int): Int? {
            var best: Int? = null
            var bestKey = Long.MAX_VALUE
            for (other in queens) {
                if (!attacks(queen, other)) continue
                val key = (chebyshev(n, other, Cell(queen)).toLong() shl 32) or other.toLong()
                if (key < bestKey) {
                    bestKey = key
                    best = other
                }
            }
            return best
        }

        private fun attacks(a: Int, b: Int): Boolean {
            if (a == b) return false
            if (QueensAttack.touches(a, b, n)) return true
            val k = puzzle.queensPerUnit
            return (a / n == b / n && inRow[a / n] > k) ||
                (a % n == b % n && inColumn[a % n] > k) ||
                (puzzle.regions[a] == puzzle.regions[b] && inRegion[puzzle.regions[a]] > k)
        }
    }
}

/** Chebyshev distance between [a] and [b] on an [n]-wide board; 0 when [b] is null, so every cell ties. */
private fun chebyshev(n: Int, a: Int, b: Cell?): Int =
    if (b == null) 0 else max(abs(a / n - b.idx / n), abs(a % n - b.idx % n))
