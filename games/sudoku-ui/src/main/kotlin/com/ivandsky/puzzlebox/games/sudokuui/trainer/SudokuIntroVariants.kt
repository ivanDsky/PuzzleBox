package com.ivandsky.puzzlebox.games.sudokuui.trainer

import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.games.sudoku.engine.hints.SudokuTechnique
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuIntro
import com.ivandsky.puzzlebox.games.sudoku.engine.trainer.SudokuTechniqueIds
import com.ivandsky.puzzlebox.gameui.trainer.IntroVariant

/**
 * What each Mini Sudoku technique's intro writes down (spec §2.7): its variants, in intro order, each with a title, the
 * rule, how to spot it and what to do. Last cell has two ([SudokuIntro]): one in a row or column and one in a box.
 * The rest have one, titled with the catalog name. Plain words (A1), and the text of a variant is the one the intro
 * and its demo caption agree on.
 */
internal object SudokuIntroVariants {

    private const val LINE = "a row or column"
    private const val BOX = "a box"

    /** What the small numbers are; the demos say it as well, on their first caption. */
    private const val NOTES = "Every open square shows its notes: small numbers for what it can still take."

    /** How to take one note off, which no screen says before the first technique that needs it. */
    private const val NOTE_OFF = "To take a note off, turn Notes on, select the square, then tap the number."

    private val variants: Map<TechniqueId, List<IntroVariant>> = mapOf(
        SudokuTechniqueIds.lastCell to unitVariants { unit ->
            IntroVariant(
                title = "Last cell in $unit",
                rule = "Every number goes once in $unit. When $unit has just one open square left, the number it is missing goes there.",
                spotIt = "${unit.capitalized()} with one open square.",
                doIt = "Put the number it is missing in that square.",
            )
        },
        id(SudokuTechnique.HIDDEN_SINGLE_BOX) to listOf(
            IntroVariant(
                title = SudokuTrainerCatalog.name(id(SudokuTechnique.HIDDEN_SINGLE_BOX)),
                rule = "$NOTES Every number goes once in each box. When a number can go in only one open square of a box, " +
                    "it goes there, even if that square's notes show other numbers too.",
                spotIt = "A box where one number's note is in only one open square.",
                doIt = "Put that number in the square.",
            ),
        ),
        id(SudokuTechnique.HIDDEN_SINGLE_LINE) to listOf(
            IntroVariant(
                title = SudokuTrainerCatalog.name(id(SudokuTechnique.HIDDEN_SINGLE_LINE)),
                rule = "Every number goes once in each row and column. When a number can go in only one open square of a row or column, " +
                    "it goes there, even if that square's notes show other numbers too.",
                spotIt = "A row or column where one number's note is in only one open square.",
                doIt = "Put that number in the square.",
            ),
        ),
        id(SudokuTechnique.NAKED_SINGLE) to listOf(
            IntroVariant(
                title = SudokuTrainerCatalog.name(id(SudokuTechnique.NAKED_SINGLE)),
                rule = "A square's notes are the numbers it can still take. When only one note is left, that number goes in the square.",
                spotIt = "An open square with one note left.",
                doIt = "Put that number in the square.",
            ),
        ),
        id(SudokuTechnique.POINTING) to listOf(
            IntroVariant(
                title = SudokuTrainerCatalog.name(id(SudokuTechnique.POINTING)),
                rule = "When a number can only go along one row or column inside a box, the box's number must be on that row or column. " +
                    "So no other square of it, outside the box, can take the number.",
                spotIt = "A box where one number's notes all sit on one row or column, and that row or column " +
                    "still shows the note outside the box.",
                doIt = "Take that number's note off the rest of the row or column. $NOTE_OFF",
            ),
        ),
        id(SudokuTechnique.CLAIMING) to listOf(
            IntroVariant(
                title = SudokuTrainerCatalog.name(id(SudokuTechnique.CLAIMING)),
                rule = "When a number fits only inside one box of a column, the column's number must be in that box. " +
                    "So no other square of the box, outside the column, can take it.",
                spotIt = "A column where one number's notes all sit in one box, and that box " +
                    "still shows the note outside the column.",
                doIt = "Take that number's note off the rest of the box. $NOTE_OFF",
            ),
        ),
        id(SudokuTechnique.NAKED_PAIR) to listOf(
            IntroVariant(
                title = SudokuTrainerCatalog.name(id(SudokuTechnique.NAKED_PAIR)),
                rule = "When two squares of a row, column or box can only take the same two numbers, those numbers must go in " +
                    "those two squares. No other square of that row, column or box can take them. " +
                    "Naked means the pair's notes are plain to see.",
                spotIt = "Two squares of one row, column or box with the same two notes and no others.",
                doIt = "Take those two notes off the rest of that row, column or box. $NOTE_OFF",
            ),
        ),
        id(SudokuTechnique.HIDDEN_PAIR) to listOf(
            IntroVariant(
                title = SudokuTrainerCatalog.name(id(SudokuTechnique.HIDDEN_PAIR)),
                rule = "When two numbers fit in only the same two squares of a row, column or box, those squares must hold them. " +
                    "Nothing else can go in those two squares. Hidden means other notes hide the pair.",
                spotIt = "Two numbers whose notes appear in only the same two squares of one row, column or box.",
                doIt = "Take every other note off those two squares. $NOTE_OFF",
            ),
        ),
    )

    /** The variants of [technique] in intro order; an id with none fails on first use, so a new technique cannot ship without. */
    fun of(technique: TechniqueId): List<IntroVariant> = variants.getValue(technique)

    /** The line variant, then the box variant, of a technique that acts on both: [variant] is told which unit it writes about. */
    private fun unitVariants(variant: (unit: String) -> IntroVariant): List<IntroVariant> =
        listOf(variant(LINE), variant(BOX))

    private fun String.capitalized(): String = replaceFirstChar(Char::uppercase)

    private fun id(technique: SudokuTechnique): TechniqueId = SudokuTechniqueIds.byTechnique.getValue(technique)
}
