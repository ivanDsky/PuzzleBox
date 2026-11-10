package com.ivandsky.puzzlebox.games.sudokuui.hint

import com.ivandsky.puzzlebox.designsystem.spotlight.Spotlight
import com.ivandsky.puzzlebox.gameui.hint.StepHints
import com.ivandsky.puzzlebox.games.sudoku.SudokuCommand
import com.ivandsky.puzzlebox.games.sudoku.SudokuSession
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.step.SudokuStep
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.step.SudokuStepper

/**
 * Mini Sudoku behind [StepHints]: snapshots the live [session], runs [SudokuStepper] off the main thread and maps the
 * step to its Spotlight. A press never places or erases anything, a reveal included (A1); a culprit is only marked (A4).
 *
 * It also owns `known` (A7): the engine ignores notes, so a shown elimination leaves nothing it can read, and it would
 * come back on every press. The struck masks of the elimination on screen are remembered on the next input and fed
 * back to the stepper; memory only, never persisted (A21). Main-confined; the compute block only reads copies.
 */
internal class SudokuStepGame(
    private val session: () -> SudokuSession,
) : StepHints.Game<SudokuStep, SudokuSpotlightMapper.Key> {

    /** Cell to struck digit mask. Replaced, never mutated, so a captured reference is a snapshot. */
    private var known: Map<Int, Int> = emptyMap()

    /** The step on screen, set by [spotlight] and cleared by any input. */
    private var shown: SudokuStep? = null

    override fun capture(): () -> SudokuStep {
        val current = session()
        val puzzle = current.puzzle
        val board = current.board
        val lastMove = current.history.done.lastOrNull()?.let(::cellOf)
        val known = known
        return { SudokuStepper.next(puzzle, board, lastMove, known) }
    }

    override fun keyOf(step: SudokuStep): SudokuSpotlightMapper.Key? = SudokuSpotlightMapper.keyOf(step)

    /** The press-time board: any input invalidates the search before it delivers, so the live board is the snapshot. */
    override fun spotlight(step: SudokuStep, playId: Int): Spotlight {
        val current = session()
        shown = step
        return SudokuSpotlightMapper.map(step, current.puzzle, current.board, playId)
    }

    /** A re-press of the same step is no input, so only the next real input accepts a shown elimination. */
    override fun inputMade() {
        (shown as? SudokuStep.Elimination)?.let { known = known.orMasks(it.struck) }
        shown = null
    }

    override fun reset() {
        known = emptyMap()
        shown = null
    }

    /** The cell a player's own edit touched; a clear or a hint reveal names none, so no new state is tracked. */
    private fun cellOf(command: SudokuCommand): Int? = when (command) {
        is SudokuCommand.Place -> command.idx
        is SudokuCommand.Erase -> command.idx
        is SudokuCommand.ToggleNote -> command.idx
        is SudokuCommand.Clear, is SudokuCommand.HintReveal -> null
    }

    private fun Map<Int, Int>.orMasks(struck: Map<Int, Int>): Map<Int, Int> =
        toMutableMap().also { merged -> struck.forEach { (cell, mask) -> merged[cell] = (merged[cell] ?: 0) or mask } }
}
