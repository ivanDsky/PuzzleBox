package com.ivandsky.puzzlebox.games.sudoku.engine.trainer

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TechniqueWalker
import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.step.SudokuStep
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.step.SudokuStepper
import com.ivandsky.puzzlebox.games.sudoku.engine.solve.SudokuSolver

/**
 * The Mini Sudoku [TechniqueWalker]: solves a puzzle from its givens with [SudokuStepper]'s own choice
 * at every step and counts the steps per [SudokuTechniqueIds] id. A reveal, a triple and the walk's end count for nothing.
 */
object SudokuTechniqueWalker : TechniqueWalker {

    /** Empty for another game's puzzle, one that does not decode, or one whose stored solution is not its unique solution. */
    override fun techniqueCounts(puzzle: Puzzle): Map<TechniqueId, Int> {
        if (puzzle.gameId != SudokuGames.MINI.id) return emptyMap()
        val decoded = (SudokuGames.MINI.decodePuzzle(puzzle.payload) as? DecodeResult.Ok)?.value ?: return emptyMap()
        if (!hasUniqueSolution(decoded)) return emptyMap()
        return walk(decoded).mapNotNull { it.technique }.groupingBy { it }.eachCount()
    }

    /** [puzzle]'s givens have exactly one solution and it is the stored one, so the walk's reveals and mistake checks are sound. */
    internal fun hasUniqueSolution(puzzle: SudokuPuzzle): Boolean {
        val result = SudokuSolver.solve(puzzle.geometry, puzzle.givens, limit = 2)
        return result.count == 1 && result.solution == puzzle.solution
    }

    /**
     * Every step [SudokuStepper] takes solving [puzzle] from its givens, driven as play drives it: a
     * single places its digit and becomes the last move; an elimination adds its strikes to the known
     * ones (play's `inputMade`) and leaves the last move alone; a reveal places its digit and, as a
     * hint reveal in play, names no last move. The walk ends with [SudokuStep.Solved].
     *
     * @throws IllegalStateException when the stepper finds a mistake (a walk only makes sound moves) or
     *   the walk outlasts the cell count plus 40 steps.
     */
    internal fun walk(puzzle: SudokuPuzzle): List<SudokuWalkStep> {
        val steps = ArrayList<SudokuWalkStep>()
        var board = SudokuBoard.of(puzzle)
        var known: Map<Int, Int> = emptyMap()
        var lastMove: Int? = null
        repeat(puzzle.geometry.cellCount + 40) {
            val step = SudokuStepper.next(puzzle, board, lastMove, known)
            steps += SudokuWalkStep(board, known, SudokuTechniqueIds.of(step), step)
            when (step) {
                SudokuStep.Solved -> return steps
                is SudokuStep.Mistake -> error("the walk met $step")
                is SudokuStep.Single -> {
                    board = board.with(step.place.cell, SudokuCell.Entry(step.place.digit))
                    lastMove = step.place.cell
                }
                is SudokuStep.Reveal -> {
                    board = board.with(step.cell, SudokuCell.Entry(step.digit))
                    lastMove = null
                }
                is SudokuStep.Elimination -> known = known.orMasks(step.struck)
            }
        }
        error("the walk did not finish within ${puzzle.geometry.cellCount + 40} steps")
    }

    private fun Map<Int, Int>.orMasks(struck: Map<Int, Int>): Map<Int, Int> =
        toMutableMap().also { merged -> struck.forEach { (cell, mask) -> merged[cell] = (merged[cell] ?: 0) or mask } }
}

/**
 * One step of [SudokuTechniqueWalker.walk]: [step] as the stepper chose it on [boardBefore] with the
 * strikes [struckBefore] (cell to digit mask) known. [technique] is the step's id; null for a reveal and the walk's end.
 */
internal data class SudokuWalkStep(
    val boardBefore: SudokuBoard,
    val struckBefore: Map<Int, Int>,
    val technique: TechniqueId?,
    val step: SudokuStep,
)
