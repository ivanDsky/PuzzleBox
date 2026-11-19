package com.ivandsky.puzzlebox.games.queens.engine.trainer

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TechniqueWalker
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensBoard
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.codec.QueensCodec
import com.ivandsky.puzzlebox.games.queens.engine.hints.step.QueensStep
import com.ivandsky.puzzlebox.games.queens.engine.hints.step.QueensStepper
import com.ivandsky.puzzlebox.games.queens.engine.rules.QueensVerifier

/**
 * The Queens [TechniqueWalker]: solves a puzzle from the empty board with [QueensStepper]'s own
 * choice at every step and counts the steps per rung [com.ivandsky.puzzlebox.games.queens.engine.hints.ladder.QueensRung.trainerId].
 * The walk plays with Auto-cross on, as a snippet board shows it (A11), so the crosses a queen
 * forces are never a step of their own; a rung with no trainer id and a reveal count for nothing.
 */
object QueensTechniqueWalker : TechniqueWalker {

    /** Empty for another game's puzzle, one that does not decode, or one without a true solution to walk by. */
    override fun techniqueCounts(puzzle: Puzzle): Map<TechniqueId, Int> {
        if (puzzle.gameId != QueensDefinition.id) return emptyMap()
        val decoded = (QueensCodec.decode(puzzle.payload) as? DecodeResult.Ok)?.value ?: return emptyMap()
        val solution = decoded.solution ?: return emptyMap()
        if (!QueensVerifier.isSolution(decoded, solution.toSet())) return emptyMap()
        return walk(decoded).mapNotNull { it.technique }.groupingBy { it }.eachCount()
    }

    /**
     * Every step [QueensStepper] takes solving [puzzle] from the empty board, ending with
     * [QueensStep.Solved] (or [QueensStep.Unavailable], which a puzzle with a solution never reaches).
     * Each step is played as the player would: a cross step crosses its cells, a last-cell step
     * stands its queen, a reveal crosses its off-solution cell. The next press's last move is the
     * step's own result cell, the highest one for a cross step.
     *
     * @throws IllegalArgumentException when [puzzle] has no solution.
     * @throws IllegalStateException when the stepper finds a mistake (a walk only makes proven
     *   moves) or the walk outlasts twice the cell count.
     */
    internal fun walk(puzzle: QueensPuzzle): List<QueensWalkStep> {
        val solution = requireNotNull(puzzle.solution) { "a walk needs the puzzle's solution" }.toSet()
        val steps = ArrayList<QueensWalkStep>()
        var board = QueensBoard()
        var lastMove: Cell? = null
        repeat(puzzle.cellCount * 2) {
            val step = QueensStepper.next(puzzle, board, autoCross = true, lastMove = lastMove, solution = solution)
            steps += QueensWalkStep(board, (step as? QueensStep.Deduction)?.unit?.rung?.trainerId, step)
            when (step) {
                QueensStep.Solved, QueensStep.Unavailable -> return steps
                is QueensStep.Mistake -> error("the walk met $step on $board")
                is QueensStep.Reveal -> {
                    board = board.copy(manual = board.manual + step.cell)
                    lastMove = step.cell
                }
                is QueensStep.Deduction -> when (val result = step.result) {
                    is QueensStep.Deduction.Result.Cross -> {
                        board = board.copy(manual = board.manual + result.cells)
                        lastMove = result.cells.maxBy { it.idx }
                    }
                    is QueensStep.Deduction.Result.LastCell -> {
                        board = board.copy(queens = board.queens + result.cell)
                        lastMove = result.cell
                    }
                }
            }
        }
        error("the walk did not finish within ${puzzle.cellCount * 2} steps")
    }
}

/**
 * One step of [QueensTechniqueWalker.walk]: [step] as the stepper chose it on [boardBefore].
 * [technique] is the proving rung's trainer id; null for a reveal, a rung that is no Trainer
 * technique, and the walk's end.
 */
internal data class QueensWalkStep(val boardBefore: QueensBoard, val technique: TechniqueId?, val step: QueensStep)
