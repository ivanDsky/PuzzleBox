package com.ivandsky.puzzlebox.games.tango.engine.trainer

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TechniqueWalker
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import com.ivandsky.puzzlebox.games.tango.codec.TangoCodec
import com.ivandsky.puzzlebox.games.tango.engine.TangoUniqueness
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStep
import com.ivandsky.puzzlebox.games.tango.engine.step.TangoStepper

/**
 * The Tango [TechniqueWalker]: solves a puzzle from its givens with [TangoStepper]'s own choice at
 * every step and counts the deductions per rung [com.ivandsky.puzzlebox.games.tango.engine.step.TangoStepRung.trainerId].
 * A reveal counts for nothing.
 */
object TangoTechniqueWalker : TechniqueWalker {

    /** Empty for another game's puzzle, one that does not decode, or one without a unique solution. */
    override fun techniqueCounts(puzzle: Puzzle): Map<TechniqueId, Int> {
        if (puzzle.gameId != TangoDefinition.id) return emptyMap()
        val decoded = (TangoCodec.decode(puzzle.payload) as? DecodeResult.Ok)?.value ?: return emptyMap()
        val solution = TangoUniqueness.solve(decoded) ?: return emptyMap()
        return walk(decoded, solution).mapNotNull { it.technique }.groupingBy { it }.eachCount()
    }

    /**
     * Every step [TangoStepper] takes solving [puzzle] from its givens, driven as in play: each
     * call gets the previous step's cell as the last move. A deduction places its value, a reveal
     * its own; the walk ends with [TangoStep.Solved] (or [TangoStep.Unavailable], which a solved
     * puzzle never reaches).
     *
     * @throws IllegalStateException when the stepper finds a mistake (a walk only makes proven
     *   moves) or the walk outlasts twice the cell count.
     */
    internal fun walk(puzzle: TangoPuzzle, solution: List<TangoCell>): List<TangoWalkStep> {
        val steps = ArrayList<TangoWalkStep>()
        val grid = MutableList<TangoCell?>(puzzle.cellCount) { puzzle.givens[it] }
        var lastMove: Int? = null
        repeat(puzzle.cellCount * 2) {
            val before = grid.toList()
            val step = TangoStepper.next(puzzle, before, lastMove, solution)
            steps += TangoWalkStep(before, (step as? TangoStep.Deduction)?.rung?.trainerId, step)
            when (step) {
                TangoStep.Solved, TangoStep.Unavailable -> return steps
                is TangoStep.Mistake -> error("the walk met $step on $before")
                is TangoStep.Reveal -> {
                    grid[step.idx] = step.value
                    lastMove = step.idx
                }
                is TangoStep.Deduction -> {
                    grid[step.idx] = step.value
                    lastMove = step.idx
                }
            }
        }
        error("the walk did not finish within ${puzzle.cellCount * 2} steps")
    }
}

/**
 * One step of [TangoTechniqueWalker.walk]: [step] as the stepper chose it on [gridBefore].
 * [technique] is the proving rung's trainer id; null for a reveal and the walk's end.
 */
internal data class TangoWalkStep(val gridBefore: List<TangoCell?>, val technique: TechniqueId?, val step: TangoStep)
