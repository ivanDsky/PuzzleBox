package com.ivandsky.puzzlebox.games.zipui

import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.ZipPuzzle
import com.ivandsky.puzzlebox.games.zip.engine.ZipHint
import com.ivandsky.puzzlebox.games.zip.engine.ZipHints
import com.ivandsky.puzzlebox.games.zip.engine.ZipState

/**
 * Where the wrong part of the line starts (ticket 1585): the cells after the last one that agrees
 * with the solution, that cell included. It is the trim point of the Hint ([ZipHint.Backtrack]),
 * so the drag and the hint can never disagree about what is wrong. A board with no stored solution
 * asks the solver, so the answer for the last line is kept for the many samples of one drag.
 */
internal class ZipWrongPart(private val puzzle: ZipPuzzle, private val hints: ZipHints = ZipHints()) {
    private var lastPath: List<Cell>? = null
    private var lastCutFrom = 0

    /** The index in [path] of the first cell of the wrong part, or `path.size` when the line is all right so far. */
    fun cutFrom(path: List<Cell>): Int {
        if (path != lastPath) {
            lastCutFrom = if (searchesPastEnd(path)) path.size else when (val hint = hints.hint(ZipState(puzzle, path))) {
                is ZipHint.Backtrack -> hint.trimTo
                else -> path.size
            }
            lastPath = path
        }
        return lastCutFrom
    }

    // The hint's search of a board with no stored solution reads the cell after a won line, which is not there.
    private fun searchesPastEnd(path: List<Cell>) = puzzle.solution == null && path.size == puzzle.board.cellCount
}
