package com.ivandsky.puzzlebox.games.sudokuui.hint

import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.SudokuGeometry
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuUnit
import com.ivandsky.puzzlebox.games.sudoku.UnitKind
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuTechnique
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.step.SudokuStep

/**
 * The callout line for a [SudokuStep] (catalog `sudoku-deductions.md`): one line, starting with the technique name,
 * naming what the Spotlight draws. Keyed by technique like `QueensRungCopy` is keyed by rung, so no `when` over
 * techniques exists to hide a missing entry; the last-cell single has its own entry because it has no technique.
 * [board] must be the board the step was computed from, or the digits named may be stale.
 */
internal object SudokuStepCopy {

    private val singles: Map<SudokuTechnique, (SudokuStep.Single, SudokuBoard) -> String> = mapOf(
        SudokuTechnique.HIDDEN_SINGLE_BOX to { step, _ ->
            "Hidden single (box): in this box only this cell can take ${step.place.digit}"
        },
        SudokuTechnique.HIDDEN_SINGLE_LINE to { step, _ ->
            "Hidden single (line): in ${unitName(requireNotNull(step.place.unit))} only this cell can take ${step.place.digit}"
        },
        SudokuTechnique.NAKED_SINGLE to { step, board ->
            val seen = step.blockers.map { board.digitAt(it) }.distinct().sorted()
            "Naked single: it sees ${seen.joinToString(", ")}, so it is ${step.place.digit}"
        },
    )

    private val lastCell: (SudokuStep.Single, SudokuUnit) -> String = { step, unit ->
        "Last cell: ${unitName(unit)} only needs ${step.place.digit}"
    }

    private val eliminations: Map<SudokuTechnique, (SudokuStep.Elimination, SudokuPuzzle) -> String> = mapOf(
        SudokuTechnique.POINTING to { step, puzzle ->
            val line = sharedLine(step, puzzle.geometry)
            val digit = digitsOf(step.group).single()
            "Pointing: this box puts its $digit in $line, so the rest of $line can't take $digit"
        },
        SudokuTechnique.CLAIMING to { step, _ ->
            val unit = unitName(requireNotNull(step.unit))
            val digit = digitsOf(step.group).single()
            "Claiming: $unit puts its $digit in this box, so the rest of the box can't take $digit"
        },
        SudokuTechnique.NAKED_PAIR to { step, _ ->
            "Naked pair: these two cells only take ${digitList(step.group)}, so the rest of ${unitName(requireNotNull(step.unit))} loses both"
        },
        SudokuTechnique.HIDDEN_PAIR to { step, _ ->
            "Hidden pair: ${digitList(step.group)} only fit these two cells, so they take nothing else"
        },
        SudokuTechnique.NAKED_TRIPLE to { step, _ ->
            "Naked triple: these 3 cells only take ${digitList(step.group)}, so the rest of ${unitName(requireNotNull(step.unit))} loses all"
        },
        SudokuTechnique.HIDDEN_TRIPLE to { step, _ ->
            "Hidden triple: ${digitList(step.group)} only fit these 3 cells, so they take nothing else"
        },
    )

    fun text(step: SudokuStep, puzzle: SudokuPuzzle, board: SudokuBoard): String {
        return when (step) {
            is SudokuStep.Single -> step.lastCell?.let { lastCell(step, it) }
                ?: checkNotNull(singles[step.place.technique]) { "no copy for ${step.place.technique}" }(step, board)
            is SudokuStep.Elimination ->
                checkNotNull(eliminations[step.technique]) { "no copy for ${step.technique}" }(step, puzzle)
            is SudokuStep.Reveal ->
                "Reveal: ${cellName(step.cell, puzzle.geometry)} is ${step.digit} — no simple step here"
            is SudokuStep.Mistake -> mistake(step, puzzle.geometry)
            SudokuStep.Solved -> error("a solved board has no callout")
        }
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

    /** The row or column holding every struck cell of a pointing step. */
    private fun sharedLine(step: SudokuStep.Elimination, geometry: SudokuGeometry): String {
        val first = step.struck.keys.first()
        return if (step.struck.keys.all { geometry.rowOf(it) == geometry.rowOf(first) }) {
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
