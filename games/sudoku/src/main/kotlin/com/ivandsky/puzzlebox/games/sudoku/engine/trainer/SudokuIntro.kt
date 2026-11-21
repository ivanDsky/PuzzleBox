package com.ivandsky.puzzlebox.games.sudoku.engine.trainer

import com.ivandsky.puzzlebox.game.trainer.BoardKind
import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.SudokuMoves
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.UnitKind
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.step.SudokuStep

/**
 * The Mini Sudoku technique intro's engine half (spec §2.7): which variants a technique has and the recording of one
 * real step, the one that opens a [BoardKind.Focus] board of the technique, cut into the boards the demo shows.
 *
 * A technique has a variant per kind of unit its steps act on when that changes what the player looks for. Last cell
 * has two ([LINE], [BOX]): a row or column with one open square reads differently from a box with one. The rest have
 * one: a hidden single already has an id per kind, pointing is always a box's and claiming always a line's, a naked
 * single names no unit, and the pairs read the same in a row, column or box (no pair in a box occurs on any corpus board).
 */
object SudokuIntro {

    /** The variant of a step in a row or column. */
    const val LINE = 0

    /** The variant of a step in a box; a technique with two variants has it second. */
    const val BOX = 1

    /**
     * One real step of a technique variant: [before] is the position the step starts from, and [landings] the board
     * after each square of the step has landed, in order (a placement is one square; an elimination lands one cell's
     * struck notes at a time, in cell order). The last landing is [before] with the whole step made.
     */
    class Recording(val variant: Int, val step: SudokuStep, val before: SudokuBoard, val landings: List<SudokuBoard>) {

        /** The cells the step's squares land on, one per landing. */
        val squares: List<Int> get() = squaresOf(step)
    }

    /** How many variants [technique]'s intro has: two for Last cell, one otherwise. */
    fun variantCount(technique: TechniqueId): Int = if (technique == SudokuTechniqueIds.lastCell) 2 else 1

    /** The variant [step] belongs to: [BOX] for a last cell in a box, [LINE] for every other step. */
    fun variantOf(step: SudokuStep): Int =
        if (step is SudokuStep.Single && step.lastCell?.kind == UnitKind.BOX) BOX else LINE

    /**
     * The recording of variant [variant] of [technique] on [puzzle]: the first step of the easiest rung on the board a
     * focus practice opens at, so [Recording.before] is exactly [SudokuPractice.start]'s focus board. Null when the
     * technique has no focus board there and when that opening step is of another variant.
     */
    fun record(puzzle: SudokuPuzzle, technique: TechniqueId, variant: Int): Recording? {
        val before = SudokuPractice.start(puzzle, technique, BoardKind.Focus) ?: return null
        val step = SudokuLadder.easiest(puzzle, before)?.steps?.firstOrNull() ?: return null
        if (variantOf(step) != variant) return null
        return Recording(variant, step, before, landings(before, step))
    }

    private fun squaresOf(step: SudokuStep): List<Int> = when (step) {
        is SudokuStep.Single -> listOf(step.place.cell)
        is SudokuStep.Elimination -> step.struck.keys.toList()
        else -> emptyList()
    }

    private fun landings(before: SudokuBoard, step: SudokuStep): List<SudokuBoard> = when (step) {
        is SudokuStep.Elimination -> step.struck.entries.runningFold(before) { board, (cell, mask) ->
            (1..board.geometry.size).filter { mask and (1 shl (it - 1)) != 0 }
                .fold(board) { acc, digit -> SudokuMoves.toggleNote(acc, cell, digit)?.apply(acc) ?: acc }
        }.drop(1)
        else -> listOf(SudokuPractice.commands(before, step).fold(before) { acc, command -> command.apply(acc) })
    }
}
