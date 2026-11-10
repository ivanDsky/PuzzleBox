package com.ivandsky.puzzlebox.games.queens.engine.hints.ladder

import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.engine.hints.HintReason.Kind

/** The row, column and region units the rungs read, by [Kind]: one table per puzzle, never rebuilt per call. */
internal object QueensUnits {

    /** The cells (ascending) of each unit of [kind], indexed by unit. Shared: callers must not mutate it. */
    fun cells(puzzle: QueensPuzzle, kind: Kind): List<List<Int>> = when (kind) {
        Kind.ROW -> puzzle.rowCells
        Kind.COLUMN -> puzzle.columnCells
        Kind.REGION -> puzzle.regionCells
    }

    /** The index of the [kind] unit holding [cell]. */
    fun indexOf(puzzle: QueensPuzzle, kind: Kind, cell: Int): Int = when (kind) {
        Kind.ROW -> cell / puzzle.size
        Kind.COLUMN -> cell % puzzle.size
        Kind.REGION -> puzzle.regions[cell]
    }
}
