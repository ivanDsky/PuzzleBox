package com.ivandsky.puzzlebox.games.sudokuui

import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.SudokuGeometry
import com.ivandsky.puzzlebox.games.sudoku.SudokuUnit
import com.ivandsky.puzzlebox.games.sudoku.UnitKind
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuHint
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuTechnique
import com.ivandsky.puzzlebox.games.sudoku.engine.rules.SudokuConflict
import com.ivandsky.puzzlebox.games.sudoku.engine.rules.SudokuConflicts

/** Player copy for hints, conflicts and refused input (research 04 rows 0–3 and 14), positions 1-based. */
internal object SudokuHintCaptions {

    const val GIVEN_REJECTION = "That number is part of the puzzle."

    /** The standing rule break: the first conflict on [board], null when the board is legal. */
    fun violation(board: SudokuBoard): String? = SudokuConflicts.find(board).firstOrNull()?.let(::conflict)

    fun hint(hint: SudokuHint?, board: SudokuBoard): String? = when (hint) {
        is SudokuHint.Conflict -> conflict(hint.conflict)
        is SudokuHint.Wrong -> wrong(hint.cells, board)
        is SudokuHint.Place -> place(hint, board.geometry)
        is SudokuHint.Reveal ->
            "There's no simple step from here, so here's one cell to get you going: ${cell(hint.cell, board.geometry)} is ${hint.digit}."
        SudokuHint.None, null -> null
    }

    private fun conflict(conflict: SudokuConflict): String =
        "${count(conflict.cells.size)} ${conflict.digit}s in ${unit(conflict.unit)}"

    private fun wrong(cells: List<Int>, board: SudokuBoard): String {
        val only = cells.singleOrNull()
            ?: return "These numbers don't fit here — take another look at their rows, columns and boxes."
        return "The ${board.digitAt(only)} in ${cell(only, board.geometry)} doesn't fit here — " +
            "take another look at its row, column and box."
    }

    private fun place(hint: SudokuHint.Place, geometry: SudokuGeometry): String {
        val at = cell(hint.cell, geometry)
        val d = hint.digit
        val aD = "${article(d)} $d"
        val reason = when (hint.technique) {
            SudokuTechnique.HIDDEN_SINGLE_BOX ->
                "this box still needs $aD, and $at is the only empty cell in it where $aD can go."
            SudokuTechnique.HIDDEN_SINGLE_LINE -> {
                val line = hint.unit?.let(::unit) ?: "this line"
                "$line still needs $aD, and every other empty cell in it already sees $aD, so it goes in $at."
            }
            else -> "$at already sees every other number in its row, column and box, so the only number left for it is $d."
        }
        return if (hint.via != null) "After ruling out some numbers, $reason" else reason.replaceFirstChar { it.uppercaseChar() }
    }

    private fun cell(idx: Int, geometry: SudokuGeometry): String =
        "row ${geometry.rowOf(idx) + 1}, column ${geometry.colOf(idx) + 1}"

    /** "an 8", "an 11"; "a" for every other digit up to 16. */
    private fun article(d: Int): String = if (d == 8 || d == 11) "an" else "a"

    private fun unit(unit: SudokuUnit): String {
        val kind = when (unit.kind) {
            UnitKind.ROW -> "row"
            UnitKind.COLUMN -> "column"
            UnitKind.BOX -> "box"
        }
        return "$kind ${unit.index + 1}"
    }

    private fun count(n: Int): String = COUNT_WORDS.getOrNull(n) ?: n.toString()

    private val COUNT_WORDS = listOf("", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine")
}
