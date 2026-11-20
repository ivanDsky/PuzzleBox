package com.ivandsky.puzzlebox.games.sudoku.engine.trainer

import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.SudokuGeometry
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuCandidates
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuTechnique
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.step.SudokuEliminations
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.step.SudokuStep
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.step.SudokuStepper

/**
 * The Mini Sudoku hint ladder as the practice reads it: the rungs in [SudokuTechniqueIds.all] order, each with every
 * deduction it holds on a board, not only the one the stepper would show. The easiest rung is the lowest one with a
 * deduction; the stepper's own `next` is not it, since the stepper defers a single the player sees at a glance.
 */
internal object SudokuLadder {

    /** A rung with at least one deduction: [steps] is every one, the cheapest first. */
    class Rung(val id: TechniqueId, val steps: List<SudokuStep>)

    /**
     * The candidates a practice board stands on: the engine's own (givens and entries only), cut to the notes the
     * board still shows. The notes are the truth here: a practice board starts with every candidate noted, so a note
     * the player took off is a strike the ladder honours, and an open cell with no notes at all is read as unstruck.
     */
    fun candidates(puzzle: SudokuPuzzle, board: SudokuBoard): IntArray {
        val candidates = SudokuCandidates.of(puzzle, board)
        board.cells.forEachIndexed { idx, cell -> if (cell is SudokuCell.Notes) candidates[idx] = candidates[idx] and cell.mask }
        return candidates
    }

    /** The lowest rung with a deduction on [board]; null on a full board and when no rung applies. */
    fun easiest(puzzle: SudokuPuzzle, board: SudokuBoard): Rung? {
        if (board.isFull) return null
        val geometry = puzzle.geometry
        val candidates = candidates(puzzle, board)
        val scan = SudokuStepper.Scan(geometry, board, candidates, lastMove = null)
        return rungs(geometry, scan, candidates).firstNotNullOfOrNull { (id, found) ->
            found().takeIf { it.isNotEmpty() }?.let { Rung(id, it) }
        }
    }

    private fun rungs(
        geometry: SudokuGeometry,
        scan: SudokuStepper.Scan,
        candidates: IntArray,
    ): List<Pair<TechniqueId, () -> List<SudokuStep>>> = listOf(
        SudokuTechniqueIds.lastCell to { singles(scan.lastCells()) },
        id(SudokuTechnique.HIDDEN_SINGLE_BOX) to { singles(scan.hiddenSingles(SudokuTechnique.HIDDEN_SINGLE_BOX)) },
        id(SudokuTechnique.HIDDEN_SINGLE_LINE) to { singles(scan.hiddenSingles(SudokuTechnique.HIDDEN_SINGLE_LINE)) },
        id(SudokuTechnique.NAKED_SINGLE) to { singles(scan.nakedSingles()) },
        id(SudokuTechnique.POINTING) to { eliminations(SudokuEliminations.intersections(geometry, candidates, pointing = true)) },
        id(SudokuTechnique.CLAIMING) to { eliminations(SudokuEliminations.intersections(geometry, candidates, pointing = false)) },
        id(SudokuTechnique.NAKED_PAIR) to { eliminations(SudokuEliminations.nakedPairs(geometry, candidates)) },
        id(SudokuTechnique.HIDDEN_PAIR) to { eliminations(SudokuEliminations.hiddenPairs(geometry, candidates)) },
    )

    /** Cheapest first (the shown evidence), then the lowest cell: the order the stepper itself ranks a rung's singles in with no last move. */
    private fun singles(found: List<SudokuStepper.Scan.Ranked>): List<SudokuStep> =
        found.sortedWith(compareBy({ it.cost }, { it.single.place.cell })).map { it.single }

    /** Smallest proof first; a tie keeps the engine's own scan order. */
    private fun eliminations(found: List<SudokuStep.Elimination>): List<SudokuStep> = found.sortedBy { it.evidence.size }

    private fun id(technique: SudokuTechnique): TechniqueId = SudokuTechniqueIds.byTechnique.getValue(technique)
}
