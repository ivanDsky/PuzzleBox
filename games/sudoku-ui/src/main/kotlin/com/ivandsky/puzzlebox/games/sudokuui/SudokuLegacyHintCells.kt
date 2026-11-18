package com.ivandsky.puzzlebox.games.sudokuui

import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuHint

// The cells the legacy (staged) hint paints. Split out of SudokuBoardState to keep it under the file cap.

/** The cell the hint places into, null for hints that point at a set of cells. */
val SudokuBoardState.hintTarget: Int?
    get() = when (val h = hint) {
        is SudokuHint.Place -> h.cell
        is SudokuHint.Reveal -> h.cell
        else -> null
    }

/** The cells that justify or make up the hint, the target excluded. */
val SudokuBoardState.hintCells: Set<Int>
    get() {
        val geometry = session.board.geometry
        return when (val h = hint) {
            is SudokuHint.Place -> {
                // A naked single is justified by everything the cell sees, not by one unit.
                val cells = h.unit?.let(geometry::cellsOf) ?: geometry.peersOf(h.cell)
                cells.filterTo(HashSet()) { it != h.cell }
            }
            is SudokuHint.Conflict -> h.conflict.cells.toSet()
            is SudokuHint.Wrong -> h.cells.toSet()
            is SudokuHint.Reveal, SudokuHint.None, null -> emptySet()
        }
    }

/** The cells whose candidates the hint's eliminations cleared, stage 1 only (spec A8). */
val SudokuBoardState.hintStruck: Set<Int>
    get() = (hint as? SudokuHint.Place)?.takeUnless { hintRevealed }?.struck?.toSet().orEmpty()
