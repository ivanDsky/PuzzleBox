package com.ivandsky.puzzlebox.games.sudoku.engine.trainer

import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.game.trainer.TrainerVerdict
import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuCandidates
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuTechnique
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.step.SudokuEliminations
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.step.SudokuStepper

/**
 * Judges one snippet move against the snippet's target technique (spec A4): [TrainerVerdict.Right]
 * when that technique proves the move on the board, [TrainerVerdict.AnotherWay] when the move fits
 * the solution all the same, [TrainerVerdict.Wrong] when it breaks it.
 *
 * "Proves" reads the technique's full enumeration on the board (every placement or strike it can
 * make), not only the one step the stepper would show.
 */
object SudokuMoveCheck {

    /**
     * [move] on [boardBefore] with [struckBefore] (cell to digit mask) struck from the candidates,
     * judged for [technique]. A single technique is proved by a [SudokuTrainerMove.Place], an
     * elimination technique by a [SudokuTrainerMove.RemoveNote]; the other kind never proves it.
     *
     * @throws IllegalArgumentException when [technique] is no Sudoku technique, or [move] is off the
     *   board, a place on a filled cell, or a note removal on a filled cell or of a digit the board
     *   does not show as a candidate.
     */
    fun classify(
        puzzle: SudokuPuzzle,
        boardBefore: SudokuBoard,
        struckBefore: Map<Int, Int>,
        technique: TechniqueId,
        move: SudokuTrainerMove,
    ): TrainerVerdict {
        require(technique in SudokuTechniqueIds.all) { "$technique is not a Sudoku technique" }
        val geometry = puzzle.geometry
        val (idx, digit) = when (move) {
            is SudokuTrainerMove.Place -> move.idx to move.digit
            is SudokuTrainerMove.RemoveNote -> move.idx to move.digit
        }
        require(idx in 0 until geometry.cellCount && digit in 1..geometry.size) { "$move is off the board" }
        require(boardBefore.digitAt(idx) == 0) { "$move is on a filled cell" }
        val candidates = SudokuCandidates.of(puzzle, boardBefore)
        for ((cell, mask) in struckBefore) candidates[cell] = candidates[cell] and mask.inv()
        val bit = 1 shl (digit - 1)
        if (move is SudokuTrainerMove.RemoveNote) require(candidates[idx] and bit != 0) { "$move strikes no shown candidate" }

        val fits = when (move) {
            is SudokuTrainerMove.Place -> digit == puzzle.solution[idx]
            is SudokuTrainerMove.RemoveNote -> digit != puzzle.solution[idx]
        }
        if (!fits) return TrainerVerdict.Wrong
        return if (proves(puzzle, boardBefore, candidates, technique, move)) TrainerVerdict.Right else TrainerVerdict.AnotherWay
    }

    private fun proves(
        puzzle: SudokuPuzzle,
        board: SudokuBoard,
        candidates: IntArray,
        technique: TechniqueId,
        move: SudokuTrainerMove,
    ): Boolean {
        val geometry = puzzle.geometry
        val scan = SudokuStepper.Scan(geometry, board, candidates, lastMove = null)
        val singles = when (technique.value) {
            "last-cell" -> scan.lastCells()
            "hidden-single-box" -> scan.hiddenSingles(SudokuTechnique.HIDDEN_SINGLE_BOX)
            "hidden-single-line" -> scan.hiddenSingles(SudokuTechnique.HIDDEN_SINGLE_LINE)
            "naked-single" -> scan.nakedSingles()
            else -> null
        }
        if (singles != null) {
            return move is SudokuTrainerMove.Place &&
                singles.any { it.single.place.cell == move.idx && it.single.place.digit == move.digit }
        }
        val eliminations = when (technique.value) {
            "pointing" -> SudokuEliminations.intersections(geometry, candidates, pointing = true)
            "claiming" -> SudokuEliminations.intersections(geometry, candidates, pointing = false)
            "naked-pair" -> SudokuEliminations.nakedPairs(geometry, candidates)
            else -> SudokuEliminations.hiddenPairs(geometry, candidates)
        }
        return move is SudokuTrainerMove.RemoveNote &&
            eliminations.any { (it.struck[move.idx] ?: 0) and (1 shl (move.digit - 1)) != 0 }
    }
}
