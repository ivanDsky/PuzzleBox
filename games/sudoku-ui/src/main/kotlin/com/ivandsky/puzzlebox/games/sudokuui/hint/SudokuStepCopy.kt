package com.ivandsky.puzzlebox.games.sudokuui.hint

import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.SudokuGeometry
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuUnit
import com.ivandsky.puzzlebox.games.sudoku.UnitKind
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuTechnique
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.step.SudokuStep
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuTechniqueIds

/**
 * The callout line for a [SudokuStep] (catalog `sudoku-deductions.md`): one line naming what the Spotlight draws.
 * Keyed by [TechniqueId], so no `when` over techniques exists to hide a missing entry; the last-cell single has
 * its own entry because it has no technique. [board] must be the board the step was computed from, or the digits
 * named may be stale.
 *
 * Every line explains the step in a sentence and keeps the technique only as a trailing parenthetical
 * (`... (hidden single)`); none opens with a technique name.
 *
 * The hint fills one template per technique with a step's own facts ([text]). The Trainer's technique list has a
 * line of its own per technique ([technique]): the hint's sentences point at the lit squares of one step, which a
 * list has none of.
 */
internal object SudokuStepCopy {

    /**
     * The words a template names, supplied by a step. Each is read only by the templates that name it, so a step
     * that lacks one (a naked single has no unit) never computes it. The second mention of a digit or a line
     * ([again], [lineAgain]) is a field of its own so a template can word the repeat on its own.
     */
    private class Facts(
        /** The unit the step names: `row 1`. */
        val unit: () -> String = missing,
        /** The digit the step places or a hidden single owns: `4`. */
        val digit: () -> String = missing,
        /** The digit a naked single comes to: `4`. */
        val answer: () -> String = missing,
        /** What a naked single's cell sees and has ruled out, as a clause: `it sees 1, 3 and has 2 ruled out`. */
        val reason: () -> String = missing,
        /** The digit an elimination's unit holds in one place, after "its". */
        val own: () -> String = missing,
        /** That digit again, after "can't take". */
        val again: () -> String = missing,
        /** The row or column a pointing step's box puts its digit in. */
        val line: () -> String = missing,
        /** That line again, after "the rest of". */
        val lineAgain: () -> String = missing,
        /** The digits of a pair, as a list: `2 and 5`. */
        val digits: () -> String = missing,
    )

    private val templates: Map<TechniqueId, (Facts) -> String> = mapOf(
        SudokuTechniqueIds.lastCell to { facts ->
            "${facts.unit().sentence()} has one open square left, and it needs ${facts.digit()}"
        },
        idOf(SudokuTechnique.HIDDEN_SINGLE_BOX) to { facts ->
            "In this box only this square can take ${facts.digit()} (hidden single)"
        },
        idOf(SudokuTechnique.HIDDEN_SINGLE_LINE) to { facts ->
            "In ${facts.unit()} only this square can take ${facts.digit()} (hidden single)"
        },
        idOf(SudokuTechnique.NAKED_SINGLE) to { facts ->
            "${facts.reason().sentence()}, so it is ${facts.answer()} (naked single)"
        },
        idOf(SudokuTechnique.POINTING) to { facts ->
            "This box puts its ${facts.own()} in ${facts.line()}, so the rest of ${facts.lineAgain()} can't take ${facts.again()} (pointing)"
        },
        idOf(SudokuTechnique.CLAIMING) to { facts ->
            "${facts.unit().sentence()} puts its ${facts.own()} in this box, so the rest of the box can't take ${facts.again()} (claiming)"
        },
        idOf(SudokuTechnique.NAKED_PAIR) to { facts ->
            "Two squares only take ${facts.digits()}, so the rest of ${facts.unit()} loses both (naked pair)"
        },
        idOf(SudokuTechnique.HIDDEN_PAIR) to { facts ->
            "${facts.digits().sentence()} fit only these two squares, so they keep no other note (hidden pair)"
        },
    )

    /** The line for [step]. */
    fun text(step: SudokuStep, puzzle: SudokuPuzzle, board: SudokuBoard): String = when (step) {
        is SudokuStep.Single -> templateOf(step)(singleFacts(step, board))
        is SudokuStep.Elimination -> templateOf(step)(eliminationFacts(step, puzzle.geometry))
        is SudokuStep.Reveal -> "${cellName(step.cell, puzzle.geometry).sentence()} is ${step.digit} — no simple step here"
        is SudokuStep.Mistake -> mistake(step, puzzle.geometry)
        SudokuStep.Solved -> error("a solved board has no callout")
    }

    /**
     * The Trainer's technique list line for [id]: a sentence of its own in generic words, with no "this" (nothing is
     * pointed at on a list), no digit or cell, and no technique name in brackets. The hint's sentences above cannot
     * serve: they point at the lit squares of one step.
     */
    fun technique(id: TechniqueId): String = lines.getValue(id)

    private val lines: Map<TechniqueId, String> = mapOf(
        SudokuTechniqueIds.lastCell to "A row, column or box has one open square: put in the number it is missing.",
        idOf(SudokuTechnique.HIDDEN_SINGLE_BOX) to "A box where one number fits in only one square.",
        idOf(SudokuTechnique.HIDDEN_SINGLE_LINE) to "A row or column where one number fits in only one square.",
        idOf(SudokuTechnique.NAKED_SINGLE) to "A square with only one number it can still take: that number goes there.",
        idOf(SudokuTechnique.POINTING) to "In a box, a number fits only along one row or column, so take it off the rest of that row or column.",
        idOf(SudokuTechnique.CLAIMING) to "In a column, a number fits only inside one box, so take it off the rest of that box.",
        idOf(SudokuTechnique.NAKED_PAIR) to "Two squares in one row, column or box can only take the same two numbers, " +
            "so the rest of that row, column or box loses both.",
        idOf(SudokuTechnique.HIDDEN_PAIR) to "Two numbers fit only in the same two squares, so those squares lose every other note.",
    )

    private fun idOf(technique: SudokuTechnique): TechniqueId = SudokuTechniqueIds.byTechnique.getValue(technique)

    private fun templateOf(step: SudokuStep): (Facts) -> String =
        templates.getValue(checkNotNull(SudokuTechniqueIds.of(step)) { "no copy for $step" })

    private fun singleFacts(step: SudokuStep.Single, board: SudokuBoard): Facts {
        val digit = { step.place.digit.toString() }
        return Facts(
            // A last cell names the unit it completes, a hidden single in a line the line it sits in.
            unit = { unitName(requireNotNull(step.lastCell ?: step.place.unit)) },
            digit = digit,
            answer = digit,
            reason = { nakedReason(step, board) },
        )
    }

    private fun eliminationFacts(step: SudokuStep.Elimination, geometry: SudokuGeometry): Facts {
        val digit = { digitsOf(step.group).single().toString() }
        val line = { sharedLine(step, geometry) }
        return Facts(
            unit = { unitName(requireNotNull(step.unit)) },
            own = digit,
            again = digit,
            line = line,
            lineAgain = line,
            digits = { digitList(step.group) },
        )
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

    /** `box`, `row N` or `column N`: the unit [a] and [b] share, the box first. */
    private fun sharedUnitName(a: Int, b: Int, geometry: SudokuGeometry): String = when {
        geometry.boxOf(a) == geometry.boxOf(b) -> "box"
        geometry.rowOf(a) == geometry.rowOf(b) -> "row ${geometry.rowOf(a) + 1}"
        else -> "column ${geometry.colOf(a) + 1}"
    }

    private fun cellName(cell: Int, geometry: SudokuGeometry): String =
        "row ${geometry.rowOf(cell) + 1}, column ${geometry.colOf(cell) + 1}"

    private fun unitName(unit: SudokuUnit): String = when (unit.kind) {
        UnitKind.ROW -> "row ${unit.index + 1}"
        UnitKind.COLUMN -> "column ${unit.index + 1}"
        UnitKind.BOX -> "this box"
    }

    /** The row or column holding every holder of a pointing step's digit in the box: its [SudokuStep.Elimination.evidence]. */
    private fun sharedLine(step: SudokuStep.Elimination, geometry: SudokuGeometry): String {
        val first = step.evidence.first()
        return if (step.evidence.all { geometry.rowOf(it) == geometry.rowOf(first) }) {
            "row ${geometry.rowOf(first) + 1}"
        } else {
            "column ${geometry.colOf(first) + 1}"
        }
    }

    /** `5`, `5 and 6`, `1, 2 and 3`. */
    private fun digitList(mask: Int): String {
        val digits = digitsOf(mask)
        return if (digits.size < 2) digits.joinToString() else digits.dropLast(1).joinToString(", ") + " and " + digits.last()
    }

    /** The digits of [mask] ascending, bit `d-1` being digit `d` (as `SudokuCandidates` stores them). */
    fun digitsOf(mask: Int): List<Int> = (1..Int.SIZE_BITS).filter { mask and (1 shl (it - 1)) != 0 }

    private fun String.sentence(): String = replaceFirstChar(Char::uppercase)
}

private val missing: () -> String = { error("this step has no such fact") }
