package com.ivandsky.puzzlebox.games.sudokuui.trainer

import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuUnit
import com.ivandsky.puzzlebox.games.sudoku.UnitKind
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuTechnique
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.step.SudokuStep

/**
 * The plain caption of each frame of a Mini Sudoku intro demo, in the frame order of [SudokuIntroDemo]: the position
 * before the step, the evidence, one per square landing. Every line names what the board shows
 * in plain words, with the step's own digits and unit.
 */
internal object SudokuIntroCaptions {

    private const val BEFORE = "A real position. Each open square shows the digits it can still take."

    /** One caption per frame: the position before, the evidence, one per square landing; the last landing also says what to look for next. */
    fun of(step: SudokuStep, puzzle: SudokuPuzzle): List<String> {
        val (parts, landings) = when (step) {
            is SudokuStep.Single -> single(step).let { it to listOf(it.landing(0)) }
            is SudokuStep.Elimination -> elimination(step, puzzle).let { it to step.struck.values.map(it.landing) }
            else -> error("no intro caption for $step")
        }
        val told = landings.dropLast(1) + "${landings.last()} ${parts.after}"
        return listOf(BEFORE, parts.evidence) + told
    }

    /** The three lines of a step that differ by technique; [landing] is told per struck mask (0 for a placement). */
    private class Parts(val evidence: String, val landing: (Int) -> String, val after: String)

    private fun single(step: SudokuStep.Single): Parts {
        val digit = step.place.digit
        val lastCell = step.lastCell
        return when {
            lastCell != null -> Parts(
                "${unitName(lastCell).replaceFirstChar(Char::uppercase)} has just one open square.",
                { "It is missing $digit, so $digit goes here." },
                "$digit is placed. Look for a row, column or box with one open square.",
            )
            step.place.technique == SudokuTechnique.NAKED_SINGLE -> Parts(
                "Look at this square and the digits it sees.",
                { "Only $digit is left in its notes, so $digit goes here." },
                "$digit is placed. Look for a square with one note left.",
            )
            else -> Parts(
                "Look at ${unitName(requireNotNull(step.place.unit))}. Every other open square is ruled out for $digit.",
                { "Only this square can take $digit, so $digit goes here." },
                "$digit is placed. Look for a digit with one place left in a box, row or column.",
            )
        }
    }

    private fun elimination(step: SudokuStep.Elimination, puzzle: SudokuPuzzle): Parts {
        val unit = unitName(requireNotNull(step.unit))
        val group = digitList(step.group)
        val stays = { mask: Int -> "${digitList(mask)} can't stay in this square's notes." }
        return when (step.technique) {
            SudokuTechnique.POINTING -> {
                val line = sharedLine(step, puzzle)
                Parts(
                    "In this box, $group can only go on these squares. They all sit in $line.",
                    { mask -> "The box's $group is on $line, so ${stays(mask)}" },
                    "$group is off the rest of $line.",
                )
            }
            SudokuTechnique.CLAIMING -> Parts(
                "In $unit, $group can only go on these squares. They all sit in one box.",
                { mask -> "In $unit, $group can only go in this box, so ${stays(mask)}" },
                "$group is off the rest of the box.",
            )
            SudokuTechnique.NAKED_PAIR -> Parts(
                "These two squares can only take $group.",
                { mask -> "$group belong to the pair, so ${stays(mask)}" },
                "$group are gone from the rest of $unit.",
            )
            SudokuTechnique.HIDDEN_PAIR -> Parts(
                "$group fit only these two squares of $unit.",
                { mask -> "These squares must hold $group, so ${stays(mask)}" },
                "The two squares keep only $group.",
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

    /** The digits of [mask] as a list: `2`, `2 and 5`, `1, 2 and 5`. */
    private fun digitList(mask: Int): String {
        val digits = (1..Integer.SIZE).filter { mask and (1 shl (it - 1)) != 0 }
        return if (digits.size == 1) digits.single().toString() else "${digits.dropLast(1).joinToString(", ")} and ${digits.last()}"
    }
}
