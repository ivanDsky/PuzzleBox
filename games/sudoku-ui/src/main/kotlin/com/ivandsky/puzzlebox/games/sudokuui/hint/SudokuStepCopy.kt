package com.ivandsky.puzzlebox.games.sudokuui.hint

import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.SudokuGeometry
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuUnit
import com.ivandsky.puzzlebox.games.sudoku.UnitKind
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuTechnique
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.step.SudokuStep

/**
 * The callout line for a [SudokuStep] (catalog `sudoku-deductions.md`): one line naming what the Spotlight draws.
 * Keyed by technique, so no `when` over
 * techniques exists to hide a missing entry; the last-cell single has its own entry because it has no technique.
 * [board] must be the board the step was computed from, or the digits named may be stale.
 *
 * Every line explains the step in a sentence and keeps the technique only as a trailing parenthetical
 * (`... (hidden single)`); none opens with a technique name.
 */
internal object SudokuStepCopy {

    private val singles: Map<SudokuTechnique, (SudokuStep.Single, SudokuBoard) -> String> = mapOf(
        SudokuTechnique.HIDDEN_SINGLE_BOX to { step, _ ->
            "In this box only this cell can take ${step.place.digit} (hidden single)"
        },
        SudokuTechnique.HIDDEN_SINGLE_LINE to { step, _ ->
            "In ${unitName(requireNotNull(step.place.unit))} only this cell can take ${step.place.digit} (hidden single)"
        },
        SudokuTechnique.NAKED_SINGLE to { step, board ->
            "${nakedReason(step, board).replaceFirstChar(Char::uppercase)}, so it is ${step.place.digit} (naked single)"
        },
    )

    private val eliminations: Map<SudokuTechnique, (SudokuStep.Elimination, SudokuPuzzle) -> String> = mapOf(
        SudokuTechnique.POINTING to { step, puzzle ->
            val line = sharedLine(step, puzzle.geometry)
            val digit = digitsOf(step.group).single()
            "This box puts its $digit in $line, so the rest of $line can't take $digit (pointing)"
        },
        SudokuTechnique.CLAIMING to { step, _ ->
            val unit = unitName(requireNotNull(step.unit)).replaceFirstChar(Char::uppercase)
            val digit = digitsOf(step.group).single()
            "$unit puts its $digit in this box, so the rest of the box can't take $digit (claiming)"
        },
        SudokuTechnique.NAKED_PAIR to { step, _ ->
            "These two cells only take ${digitList(step.group)}, so the rest of ${unitName(requireNotNull(step.unit))} loses both (naked pair)"
        },
        SudokuTechnique.HIDDEN_PAIR to { step, _ ->
            "${digitList(step.group)} only fit these two cells, so they take nothing else (hidden pair)"
        },
    )

    /** The line for [step]. */
    fun text(step: SudokuStep, puzzle: SudokuPuzzle, board: SudokuBoard): String = when (step) {
        is SudokuStep.Single -> step.lastCell?.let { "${unitName(it).replaceFirstChar(Char::uppercase)} has one cell left, and it needs ${step.place.digit}" }
            ?: checkNotNull(singles[step.place.technique]) { "no copy for ${step.place.technique}" }(step, board)
        is SudokuStep.Elimination ->
            checkNotNull(eliminations[step.technique]) { "no copy for ${step.technique}" }(step, puzzle)
        is SudokuStep.Reveal -> "${cellName(step.cell, puzzle.geometry).replaceFirstChar(Char::uppercase)} is ${step.digit} — no simple step here"
        is SudokuStep.Mistake -> mistake(step, puzzle.geometry)
        SudokuStep.Solved -> error("a solved board has no callout")
    }

    /** What a naked single's cell sees and has ruled out, as a clause: `it sees 1, 3 and has 2 ruled out`. */
    private fun nakedReason(step: SudokuStep.Single, board: SudokuBoard): String {
        val seen = step.blockers.map { board.digitAt(it) }.distinct().sorted()
        val ruledOut = step.struck[step.place.cell] ?: 0
        val clauses = listOfNotNull(
            if (seen.isEmpty()) null else "sees ${seen.joinToString(", ")}",
            if (ruledOut == 0) null else "has ${digitList(ruledOut)} ruled out",
        )
        return "it ${clauses.joinToString(" and ")}"
    }

    private fun mistake(step: SudokuStep.Mistake, geometry: SudokuGeometry): String {
        val partner = step.conflictsWith ?: return "This ${step.digit} isn't in the solution"
        return "This ${step.digit} repeats the ${step.digit} in its ${sharedUnitName(step.cell, partner, geometry)} — and isn't in the solution"
    }

    /** `box`, `row N` or `col N`: the unit [a] and [b] share, the box first. */
    private fun sharedUnitName(a: Int, b: Int, geometry: SudokuGeometry): String = when {
        geometry.boxOf(a) == geometry.boxOf(b) -> "box"
        geometry.rowOf(a) == geometry.rowOf(b) -> "row ${geometry.rowOf(a) + 1}"
        else -> "col ${geometry.colOf(a) + 1}"
    }

    private fun cellName(cell: Int, geometry: SudokuGeometry): String =
        "row ${geometry.rowOf(cell) + 1}, column ${geometry.colOf(cell) + 1}"

    private fun unitName(unit: SudokuUnit): String = when (unit.kind) {
        UnitKind.ROW -> "row ${unit.index + 1}"
        UnitKind.COLUMN -> "col ${unit.index + 1}"
        UnitKind.BOX -> "box ${unit.index + 1}"
    }

    /** The row or column holding every holder of a pointing step's digit in the box: its [SudokuStep.Elimination.evidence]. */
    private fun sharedLine(step: SudokuStep.Elimination, geometry: SudokuGeometry): String {
        val first = step.evidence.first()
        return if (step.evidence.all { geometry.rowOf(it) == geometry.rowOf(first) }) {
            "row ${geometry.rowOf(first) + 1}"
        } else {
            "col ${geometry.colOf(first) + 1}"
        }
    }

    /** `5`, `5 and 6`, `1, 2 and 3`. */
    private fun digitList(mask: Int): String {
        val digits = digitsOf(mask)
        return if (digits.size < 2) digits.joinToString() else digits.dropLast(1).joinToString(", ") + " and " + digits.last()
    }

    /** The digits of [mask] ascending, bit `d-1` being digit `d` (as `SudokuCandidates` stores them). */
    fun digitsOf(mask: Int): List<Int> = (1..Int.SIZE_BITS).filter { mask and (1 shl (it - 1)) != 0 }
}
