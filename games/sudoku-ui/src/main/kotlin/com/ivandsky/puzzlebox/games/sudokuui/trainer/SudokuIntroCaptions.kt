package com.ivandsky.puzzlebox.games.sudokuui.trainer

import com.ivandsky.puzzlebox.games.sudoku.SudokuBoard
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuUnit
import com.ivandsky.puzzlebox.games.sudoku.UnitKind
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuTechnique
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.step.SudokuStep

/**
 * The plain caption of each frame of a Mini Sudoku intro demo, in the frame order of [SudokuIntroDemo]: the position
 * before the step, the evidence, one per square landing. Every line names what the board shows in plain words, with the
 * step's own numbers and unit.
 *
 * The train step track shows only the evidence caption and the first landing's, so the first landing's caption tells
 * the whole landing (every square, the closing line) and every later landing frame repeats it.
 */
internal object SudokuIntroCaptions {

    /** What the small numbers are: said before the first step, since the trainer filled them in and they are complete. */
    private const val NOTES = "Every open square shows, in small numbers, the numbers it can still take."

    /** One caption per frame: the position before, the evidence, then the whole landing's caption once per landing. */
    fun of(step: SudokuStep, puzzle: SudokuPuzzle, before: SudokuBoard): List<String> {
        val (parts, squares) = when (step) {
            is SudokuStep.Single -> single(step, before) to 1
            is SudokuStep.Elimination -> elimination(step, puzzle) to step.struck.size
            else -> error("no intro caption for $step")
        }
        return listOf(NOTES, "$NOTES ${parts.evidence}") + List(squares) { parts.landing }
    }

    /** The two lines of a step that differ by technique: what the evidence lights and what the whole landing does. */
    private class Parts(val evidence: String, val landing: String)

    private fun single(step: SudokuStep.Single, before: SudokuBoard): Parts {
        val digit = step.place.digit
        val lastCell = step.lastCell
        return when {
            lastCell != null -> Parts(
                "${unitName(lastCell).replaceFirstChar(Char::uppercase)} has just one open square.",
                "It is missing $digit, so $digit goes here. $digit is placed. Look for a row, column or box with one open square.",
            )
            step.place.technique == SudokuTechnique.NAKED_SINGLE -> Parts(
                nakedReason(step, before)?.let { "$it, so only $digit is left in its notes." } ?: "Only $digit is left in this square's notes.",
                "Only $digit is left in its notes, so $digit goes here. $digit is placed. Look for a square with one note left.",
            )
            else -> {
                val next = if (step.place.technique == SudokuTechnique.HIDDEN_SINGLE_BOX) "a box" else "a row or column"
                Parts(
                    "Look at ${unitName(requireNotNull(step.place.unit))}. Every other open square is ruled out for $digit.",
                    "Only this square can take $digit, so $digit goes here. $digit is placed. " +
                        "Look for $next where a number fits in only one square.",
                )
            }
        }
    }

    private fun elimination(step: SudokuStep.Elimination, puzzle: SudokuPuzzle): Parts {
        val unit = unitName(requireNotNull(step.unit))
        val group = digitList(digitsOf(step.group))
        return when (step.technique) {
            SudokuTechnique.POINTING -> {
                val line = sharedLine(step, puzzle)
                Parts(
                    "In this box, $group can only go on these squares. They all sit in $line.",
                    "The box's $group must be in $line, so $group comes off the rest of $line.",
                )
            }
            SudokuTechnique.CLAIMING -> Parts(
                "In $unit, $group can only go on these squares. They all sit in one box.",
                "In $unit, $group can only go in this box, so $group comes off the rest of the box.",
            )
            SudokuTechnique.NAKED_PAIR -> Parts(
                "These two squares can only take $group.",
                "$group must go in these two squares, so no other square in $unit can take them.",
            )
            SudokuTechnique.HIDDEN_PAIR -> Parts(
                "In $unit, the notes for $group sit in only these two squares.",
                "$group fit only these two squares of $unit, so the two squares keep only $group.",
            )
            else -> error("no intro caption for ${step.technique}")
        }
    }

    /** `row 3`, `column 2`, or `this box`. */
    private fun unitName(unit: SudokuUnit): String = when (unit.kind) {
        UnitKind.ROW -> "row ${unit.index + 1}"
        UnitKind.COLUMN -> "column ${unit.index + 1}"
        UnitKind.BOX -> "this box"
    }

    /** The row or column holding every cell of a pointing step's evidence. */
    private fun sharedLine(step: SudokuStep.Elimination, puzzle: SudokuPuzzle): String {
        val geometry = puzzle.geometry
        val first = step.evidence.first()
        return if (step.evidence.all { geometry.rowOf(it) == geometry.rowOf(first) }) {
            "row ${geometry.rowOf(first) + 1}"
        } else {
            "column ${geometry.colOf(first) + 1}"
        }
    }

    /**
     * Why a naked single's square has one note left, true after eliminations too: the numbers it shares a row, column
     * or box with ([SudokuStep.Single.blockers]), and the numbers an earlier strike took off its notes
     * ([SudokuStep.Single.struck]) that no peer holds. Either part may be missing; null when both are.
     */
    private fun nakedReason(step: SudokuStep.Single, before: SudokuBoard): String? {
        val seen = step.blockers.map { before.digitAt(it) }.filter { it != 0 }.distinct().sorted()
        val off = digitsOf(step.struck[step.place.cell] ?: 0)
        val shares = if (seen.isEmpty()) null else "shares a row, column or box with ${digitList(seen)}"
        val ruled = if (off.isEmpty()) null else "${digitList(off)} ${if (off.size == 1) "is" else "are"} already off its notes"
        return when {
            shares != null && ruled != null -> "This square $shares, and $ruled"
            shares != null -> "This square $shares"
            ruled != null -> "This square has ${digitList(off)} already off its notes"
            else -> null
        }
    }

    /** The digits of [mask] ascending, bit `d-1` being digit `d`. */
    private fun digitsOf(mask: Int): List<Int> = (1..Integer.SIZE).filter { mask and (1 shl (it - 1)) != 0 }

    /** `2`, `2 and 5`, `1, 2 and 5`. */
    private fun digitList(digits: List<Int>): String = when (digits.size) {
        0 -> ""
        1 -> digits.single().toString()
        else -> "${digits.dropLast(1).joinToString(", ")} and ${digits.last()}"
    }
}
