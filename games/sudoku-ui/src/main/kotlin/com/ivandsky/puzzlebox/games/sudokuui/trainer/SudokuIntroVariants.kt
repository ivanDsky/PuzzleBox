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

    private val variants: Map<TechniqueId, List<IntroVariant>> = mapOf(
        SudokuTechniqueIds.lastCell to unitVariants { unit ->
            IntroVariant(
                title = "Last cell in $unit",
                rule = "Every digit goes once in $unit. When $unit has just one open square left, the digit it is missing goes there.",
                spotIt = "${unit.capitalized()} with one open square.",
                doIt = "Put the digit it is missing in that square.",
            )
        },
        id(SudokuTechnique.HIDDEN_SINGLE_BOX) to listOf(
            IntroVariant(
                title = SudokuTrainerCatalog.name(id(SudokuTechnique.HIDDEN_SINGLE_BOX)),
                rule = "Every digit goes once in each box. When a digit can go in only one open square of a box, it goes there, " +
                    "even if that square's notes show other digits too.",
                spotIt = "A box where one digit's note is in only one open square.",
                doIt = "Put that digit in the square.",
            ),
        ),
        id(SudokuTechnique.HIDDEN_SINGLE_LINE) to listOf(
            IntroVariant(
                title = SudokuTrainerCatalog.name(id(SudokuTechnique.HIDDEN_SINGLE_LINE)),
                rule = "Every digit goes once in each row and column. When a digit can go in only one open square of a row or column, " +
                    "it goes there, even if that square's notes show other digits too.",
                spotIt = "A row or column where one digit's note is in only one open square.",
                doIt = "Put that digit in the square.",
            ),
        ),
        id(SudokuTechnique.NAKED_SINGLE) to listOf(
            IntroVariant(
                title = SudokuTrainerCatalog.name(id(SudokuTechnique.NAKED_SINGLE)),
                rule = "A square's notes are the digits it can still take. When only one note is left, that digit goes in the square.",
                spotIt = "An open square with one note left.",
                doIt = "Put that digit in the square.",
            ),
        ),
        id(SudokuTechnique.POINTING) to listOf(
            IntroVariant(
                title = SudokuTrainerCatalog.name(id(SudokuTechnique.POINTING)),
                rule = "When a digit can only go on one row or column inside a box, the box's digit must be on that line. " +
                    "So no other square of the line, outside the box, can take it.",
                spotIt = "A box where one digit's notes all sit on one row or column.",
                doIt = "Take that digit's note off the rest of the line.",
            ),
        ),
        id(SudokuTechnique.CLAIMING) to listOf(
            IntroVariant(
                title = SudokuTrainerCatalog.name(id(SudokuTechnique.CLAIMING)),
                rule = "When a digit can only go in one box inside a row or column, that line's digit must be in the box. " +
                    "So no other square of the box, outside the line, can take it.",
                spotIt = "A row or column where one digit's notes all sit in one box.",
                doIt = "Take that digit's note off the rest of the box.",
            ),
        ),
        id(SudokuTechnique.NAKED_PAIR) to listOf(
            IntroVariant(
                title = SudokuTrainerCatalog.name(id(SudokuTechnique.NAKED_PAIR)),
                rule = "When two squares of a row, column or box can only take the same two digits, those digits must go in " +
                    "those two squares. No other square of that row, column or box can take them.",
                spotIt = "Two squares of one row, column or box with the same two notes and no others.",
                doIt = "Take those two notes off the rest of that row, column or box.",
            ),
        ),
        id(SudokuTechnique.HIDDEN_PAIR) to listOf(
            IntroVariant(
                title = SudokuTrainerCatalog.name(id(SudokuTechnique.HIDDEN_PAIR)),
                rule = "When two digits fit in only the same two squares of a row, column or box, those squares must hold them. " +
                    "Nothing else can go in those two squares.",
                spotIt = "Two digits whose notes appear in only the same two squares of one row, column or box.",
                doIt = "Take every other note off those two squares.",
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
