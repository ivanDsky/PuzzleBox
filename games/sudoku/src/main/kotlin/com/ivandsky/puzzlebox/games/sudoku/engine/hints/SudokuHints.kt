package com.ivandsky.puzzlebox.games.sudoku.engine.hints

import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.SudokuGeometry
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuUnit
import com.ivandsky.puzzlebox.games.sudoku.UnitKind
import com.ivandsky.puzzlebox.games.sudoku.engine.rules.SudokuConflicts

/**
 * The hint ladder. Mistakes come first because a technique step on a broken board would be
 * built on a false premise; then the cheapest single, climbing the [steps] only when no
 * single applies; then a reveal so the player is never left without help.
 */
class SudokuHints internal constructor(private val steps: List<EliminationStep>) {

    constructor() : this(EliminationSteps.DEFAULT)

    fun hint(puzzle: SudokuPuzzle, board: SudokuBoard): SudokuHint {
        SudokuConflicts.find(board).firstOrNull()?.let { return SudokuHint.Conflict(it) }
        val wrong = board.cells.indices.filter { idx ->
            val cell = board.cells[idx]
            cell is SudokuCell.Entry && cell.digit != puzzle.solution[idx]
        }
        if (wrong.isNotEmpty()) return SudokuHint.Wrong(wrong)
        if (board.isFull) return SudokuHint.None

        val candidates = SudokuCandidates.of(puzzle, board)
        return place(puzzle.geometry, candidates) ?: reveal(puzzle, board, candidates)
    }

    /**
     * The next technique placement over [known] digits (0 = open), solution-free: null where only
     * a reveal is left. Conflicts are the caller's to rule out.
     */
    internal fun placement(geometry: SudokuGeometry, known: IntArray): SudokuHint.Place? =
        place(geometry, SudokuCandidates.ofKnown(geometry, known))

    private fun place(geometry: SudokuGeometry, candidates: IntArray): SudokuHint.Place? {
        var before: IntArray? = null
        var via: SudokuTechnique? = null
        var struck = emptyList<Int>()
        while (true) {
            single(geometry, candidates, via, struck)?.let { return it }
            val start = before ?: candidates.copyOf().also { before = it }
            val step = steps.firstOrNull { it.eliminate(geometry, candidates) } ?: return null
            if (via == null || step.technique > via) via = step.technique
            struck = candidates.indices.filter { candidates[it] != start[it] }
        }
    }

    internal fun single(
        geometry: SudokuGeometry,
        candidates: IntArray,
        via: SudokuTechnique?,
        struck: List<Int>,
    ): SudokuHint.Place? =
        hiddenSingle(geometry, candidates, via, struck, SudokuTechnique.HIDDEN_SINGLE_BOX) { it.kind == UnitKind.BOX }
            ?: hiddenSingle(geometry, candidates, via, struck, SudokuTechnique.HIDDEN_SINGLE_LINE) { it.kind != UnitKind.BOX }
            ?: nakedSingle(candidates, via, struck)

    /** The first unit, in units order, where some digit fits exactly one cell; lowest such digit. */
    private inline fun hiddenSingle(
        geometry: SudokuGeometry,
        candidates: IntArray,
        via: SudokuTechnique?,
        struck: List<Int>,
        technique: SudokuTechnique,
        accepts: (SudokuUnit) -> Boolean,
    ): SudokuHint.Place? {
        for (unit in geometry.units) {
            if (!accepts(unit)) continue
            val cells = geometry.cellsOf(unit)
            var once = 0
            var twice = 0
            for (cell in cells) {
                val mask = candidates[cell]
                twice = twice or (once and mask)
                once = once or mask
            }
            val singles = once and twice.inv()
            if (singles == 0) continue
            val bit = Integer.lowestOneBit(singles)
            val cell = cells.first { candidates[it] and bit != 0 }
            return SudokuHint.Place(cell, Integer.numberOfTrailingZeros(bit) + 1, technique, unit, via, struck)
        }
        return null
    }

    private fun nakedSingle(candidates: IntArray, via: SudokuTechnique?, struck: List<Int>): SudokuHint.Place? {
        for (cell in candidates.indices) {
            val mask = candidates[cell]
            if (Integer.bitCount(mask) == 1) {
                val digit = Integer.numberOfTrailingZeros(mask) + 1
                return SudokuHint.Place(cell, digit, SudokuTechnique.NAKED_SINGLE, null, via, struck)
            }
        }
        return null
    }

    /** The open cell with the fewest candidates, lowest idx on a tie. */
    private fun reveal(puzzle: SudokuPuzzle, board: SudokuBoard, candidates: IntArray): SudokuHint.Reveal {
        var best = -1
        for (cell in candidates.indices) {
            if (board.digitAt(cell) != 0) continue
            if (best == -1 || Integer.bitCount(candidates[cell]) < Integer.bitCount(candidates[best])) best = cell
        }
        return SudokuHint.Reveal(best, puzzle.solution[best])
    }
}
