package com.ivandsky.puzzlebox.games.queens.engine.trainer

import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensBoard
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensCandidates
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensDeduction
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensLadder
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensProof
import com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensRung
import com.ivandsky.puzzlebox.games.queens.engine.hints.step.QueensStep
import com.ivandsky.puzzlebox.games.queens.engine.hints.step.toStep
import com.ivandsky.puzzlebox.games.queens.engine.rules.QueensVerifier

/**
 * The Queens practice board's engine (spec §2.2): the board a practice starts from, and the verdict on each move
 * the player makes from there, against the easiest rung of [QueensLadder.Stepwise] that has a deduction on the board.
 *
 * Boards read as the live board shows them with Auto-cross on (A11): the queens, the crosses a queen forces, and the
 * manual crosses. The easiest rung is the lowest with a deduction that settles a square still open, not the
 * stepper's pick, which defers what a player sees at a glance: here the rung is what the player is asked about.
 */
object QueensPractice {

    /**
     * The board a practice of [technique] starts from: [puzzle] walked from the empty board, a deduction of the
     * easiest rung at every point (the simplest of its instances, as Show me makes it), up to the first point where
     * [technique] is the easiest rung that has one. Null when [technique] is no Queens technique, [puzzle] has no true
     * solution, or the walk never meets [technique] (it solves the puzzle without it, or the ladder stalls first).
     */
    fun start(puzzle: QueensPuzzle, technique: TechniqueId): QueensBoard? {
        if (QueensLadder.Stepwise.rungs.none { it.trainerId == technique }) return null
        return walk(puzzle).firstOrNull { (_, easiest) -> easiest == technique }?.first
    }

    /**
     * The walk behind [start] and the technique counts: [puzzle] from the empty board, one deduction of the easiest
     * rung at every point (the simplest of its instances, as Show me makes it), each point as the board before the
     * deduction and the rung's trainer id (null for the attack rung, which Auto-cross never lets fire). Ends when the
     * queens are all down, or the ladder has nothing left. Empty for a puzzle without a true solution.
     */
    internal fun walk(puzzle: QueensPuzzle): Sequence<Pair<QueensBoard, TechniqueId?>> = sequence {
        val solution = puzzle.solution ?: return@sequence
        if (!QueensVerifier.isSolution(puzzle, solution.toSet())) return@sequence
        var board = QueensBoard()
        // Every step settles a square, so the cell count bounds the walk; the doubled bound is slack, not a budget.
        repeat(puzzle.cellCount * 2) {
            if (board.queens.size == puzzle.size * puzzle.queensPerUnit) return@sequence
            val candidates = candidates(puzzle, board)
            val step = easiest(puzzle, candidates) ?: return@sequence
            yield(board to step.unit.rung.trainerId)
            board = board.after(step, candidates)
        }
    }

    /**
     * [move] on [board], judged against the easiest rung there. Null when no rung has a deduction on [board]: it is
     * solved, or the ladder is stuck, and there is nothing to judge by.
     *
     * Right reads the rung's every deduction, not the simplest ones: a deduction the player finds at any depth the rung
     * covers is that technique. A rung proves only what holds in every solution, so a move that breaks the puzzle's
     * solution is never Right.
     *
     * @throws IllegalArgumentException when [puzzle] has no solution, [move] is off the board, a queen on a square that
     *   holds one, or a cross on a square that holds a queen or a mark.
     */
    fun judge(puzzle: QueensPuzzle, board: QueensBoard, move: QueensPracticeMove): QueensPracticeVerdict? {
        val solution = requireNotNull(puzzle.solution) { "judging a move needs the puzzle's solution" }
        val candidates = candidates(puzzle, board)
        val cell = move.cell
        require(cell.idx in 0 until puzzle.cellCount && cell !in board.queens && (move is QueensPracticeMove.Place || candidates.isOpen(cell.idx))) {
            "$move is off the board, a queen on a queen, or a cross on a square that is not open"
        }
        val rung = easiestRung(puzzle, candidates) ?: return null
        val fits = if (move is QueensPracticeMove.Place) cell in solution else cell !in solution
        if (!fits) return QueensPracticeVerdict.Wrong(rung, why(puzzle, board, move))
        val proof = rung.steps(puzzle, candidates)
            .filter { candidates.settles(it.deduction) && it.deduction.makes(move) }
            .minByOrNull { it.evidence.size }
            ?: return QueensPracticeVerdict.Easier(simplest(puzzle, candidates, rung))
        val step = proof.toStep()
        val left = (step.result as? QueensStep.Deduction.Result.Cross)?.cells.orEmpty().filter { it != cell && candidates.isOpen(it.idx) }
        return QueensPracticeVerdict.Right(step, left.toSet())
    }

    /** The deduction Show me makes on [board]: the simplest of the easiest rung's. Null when no rung has one. */
    fun show(puzzle: QueensPuzzle, board: QueensBoard): QueensStep.Deduction? = easiest(puzzle, candidates(puzzle, board))

    /** The squares [step] crosses that are still open on [board], lowest first; empty for a queen step. */
    fun crossings(puzzle: QueensPuzzle, board: QueensBoard, step: QueensStep.Deduction): List<Cell> =
        crossings(candidates(puzzle, board), step)

    private fun crossings(candidates: QueensCandidates, step: QueensStep.Deduction): List<Cell> =
        (step.result as? QueensStep.Deduction.Result.Cross)?.cells.orEmpty().filter { candidates.isOpen(it.idx) }.sortedBy { it.idx }

    private fun QueensBoard.after(step: QueensStep.Deduction, candidates: QueensCandidates): QueensBoard =
        when (val result = step.result) {
            is QueensStep.Deduction.Result.Cross -> copy(manual = manual + crossings(candidates, step))
            is QueensStep.Deduction.Result.LastCell -> copy(queens = queens + result.cell)
        }

    /** Queens and the crosses they force, plus the manual crosses; a cross hidden under a queen is no cross. */
    private fun candidates(puzzle: QueensPuzzle, board: QueensBoard): QueensCandidates {
        val queens = board.queens.mapTo(HashSet(board.queens.size * 2)) { it.idx }
        val crosses = board.manual.mapNotNullTo(HashSet(board.manual.size * 2)) { cell -> cell.idx.takeIf { it !in queens } }
        return QueensCandidates.start(puzzle, queens).exclude(crosses)
    }

    /** The lowest rung with a deduction that settles a square still open: the one a player is asked about. */
    private fun easiestRung(puzzle: QueensPuzzle, candidates: QueensCandidates): QueensRung? =
        QueensLadder.Stepwise.rungs.firstOrNull { rung -> rung.steps(puzzle, candidates).any { candidates.settles(it.deduction) } }

    private fun easiest(puzzle: QueensPuzzle, candidates: QueensCandidates): QueensStep.Deduction? =
        easiestRung(puzzle, candidates)?.let { simplest(puzzle, candidates, it) }

    /** The least evidence among [rung]'s simplest deductions, then the lowest square it settles; [rung] has one. */
    private fun simplest(puzzle: QueensPuzzle, candidates: QueensCandidates, rung: QueensRung): QueensStep.Deduction {
        val order = compareBy<QueensProof>({ it.evidence.size }, { proof ->
            when (val d = proof.deduction) {
                is QueensDeduction.Place -> d.cell
                is QueensDeduction.Exclude -> d.cells.min()
            }
        })
        val best = rung.simplestSteps(puzzle, candidates).filter { candidates.settles(it.deduction) }.minWithOrNull(order)
            ?: rung.steps(puzzle, candidates).filter { candidates.settles(it.deduction) }.minWith(order)
        return best.toStep()
    }

    private fun QueensCandidates.settles(deduction: QueensDeduction): Boolean = when (deduction) {
        is QueensDeduction.Place -> isOpen(deduction.cell)
        is QueensDeduction.Exclude -> deduction.cells.any(::isOpen)
    }

    private fun QueensDeduction.makes(move: QueensPracticeMove): Boolean = when (move) {
        is QueensPracticeMove.Place -> this is QueensDeduction.Place && cell == move.cell.idx
        is QueensPracticeMove.Cross -> this is QueensDeduction.Exclude && move.cell.idx in cells
    }

    private fun why(puzzle: QueensPuzzle, board: QueensBoard, move: QueensPracticeMove): QueensPracticeVerdict.Wrong.Why = when (move) {
        is QueensPracticeMove.Cross -> QueensPracticeVerdict.Wrong.Why.ON_SOLUTION
        is QueensPracticeMove.Place -> {
            val queens = board.queens.mapTo(HashSet(board.queens.size * 2)) { it.idx }
            if (QueensCandidates.start(puzzle, queens).isOpen(move.cell.idx)) {
                QueensPracticeVerdict.Wrong.Why.OFF_SOLUTION
            } else {
                QueensPracticeVerdict.Wrong.Why.ATTACKED
            }
        }
    }
}
