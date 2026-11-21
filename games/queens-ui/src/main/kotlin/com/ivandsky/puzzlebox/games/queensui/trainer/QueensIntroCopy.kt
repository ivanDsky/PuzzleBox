package com.ivandsky.puzzlebox.games.queensui.trainer

import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.gameui.trainer.IntroVariant

/**
 * The written half of a Queens technique intro: each technique's variants (title, rule, how to spot it, the move), in
 * plain words. Line lock is the one technique with two variants, in intro order: [FILL_LINE], [FILL_REGION]. Every
 * other technique has one, titled with its catalog name. A technique with no entry here fails on first use, so a new
 * technique cannot ship without an intro.
 */
internal object QueensIntroCopy {

    /** Index of Line lock's "Fill the line" variant: a color's open squares all sit in one line. */
    const val FILL_LINE = 0

    /** Index of Line lock's "Fill the region" variant: a line's open squares all belong to one color. */
    const val FILL_REGION = 1

    private val lineLock = TechniqueId("line-lock")

    private val fillLine = IntroVariant(
        title = "Fill the line",
        rule = "All of a color's open squares sit in one row or column. Its queen must be in that line, so the rest of the line is out.",
        spotIt = "A color whose open squares are all in one row or column.",
        doIt = "Cross out the rest of that row or column.",
    )

    private val fillRegion = IntroVariant(
        title = "Fill the region",
        rule = "All of a row or column's open squares belong to one color. That color's queen must be in that line, so the rest of the color is out.",
        spotIt = "A row or column where every open square is the same color.",
        doIt = "Cross out the rest of that color.",
    )

    private val single: Map<TechniqueId, (name: String) -> IntroVariant> = mapOf(
        TechniqueId("singles") to { name ->
            IntroVariant(
                title = name,
                rule = "A queen has to go in every row, column and color. When one of them has just one open square left, the queen goes on that square.",
                spotIt = "A row, column or color with one open square left.",
                doIt = "Put the queen on that square.",
            )
        },
        TechniqueId("shared-squares") to { name ->
            IntroVariant(
                title = name,
                rule = "A queen on some square would touch every open square of a row, column or color that still needs its queen. " +
                    "That leaves it no room, so the square is out.",
                spotIt = "A row, column or color with a few open squares that one other square touches all of.",
                doIt = "Cross out that other square.",
            )
        },
        TechniqueId("pigeonhole-regions") to { name ->
            IntroVariant(
                title = name,
                rule = "Some colors have all their open squares in the same number of rows or columns. Those lines have no queens to spare, " +
                    "so every other open square in them is out. It works the other way round too.",
                spotIt = "Two or more colors whose open squares all sit in the same number of rows or columns.",
                doIt = "Cross out the other open squares in those rows or columns.",
            )
        },
        TechniqueId("pigeonhole-lines") to { name ->
            IntroVariant(
                title = name,
                rule = "Some rows have all their open squares in the same number of columns. Those columns have no queens to spare, " +
                    "so every other open square in them is out. It works the other way round too.",
                spotIt = "Two or more rows whose open squares all sit in the same number of columns, or columns in rows.",
                doIt = "Cross out the other open squares in those columns or rows.",
            )
        },
        TechniqueId("leftover-queen") to { name ->
            IntroVariant(
                title = name,
                rule = "Some colors sit wholly inside a few rows or columns and use up all but one of their queens. " +
                    "The ringed squares are left to hold that last queen, so a square that would rule out all of them is out.",
                spotIt = "A few rows or columns that wholly hold one color fewer than their number, leaving some ringed squares.",
                doIt = "Cross out the square that would rule out every ringed square.",
            )
        },
        TechniqueId("squeeze") to { name ->
            IntroVariant(
                title = name,
                rule = "A queen on some square would block every open square of a row, column or color that still needs its queen. " +
                    "That leaves it no room, so the square is out.",
                spotIt = "A square outside a row, column or color whose queen would block all of that line's open squares.",
                doIt = "Cross out that square.",
            )
        },
        TechniqueId("count-squeeze") to { name ->
            IntroVariant(
                title = name,
                rule = "A queen on some square would leave a few colors with fewer lines open to them than queens they need. " +
                    "That is not enough room, so the square is out.",
                spotIt = "A square whose queen would leave some colors too little room.",
                doIt = "Cross out that square.",
            )
        },
        TechniqueId("short-look-ahead") to { name ->
            IntroVariant(
                title = name,
                rule = "Try a queen on a square. If it forces one more queen, and that leaves a row, column or color with no room, " +
                    "the first square is out.",
                spotIt = "A square whose queen forces one more square and then leaves a row, column or color no room.",
                doIt = "Cross out that square.",
            )
        },
        TechniqueId("long-look-ahead") to { name ->
            IntroVariant(
                title = name,
                rule = "Try a queen on a square. If it forces a chain of queens, and the chain ends with a row, column or color with no room, " +
                    "the first square is out.",
                spotIt = "A square whose queen sets off a chain of forced squares that ends in a dead end.",
                doIt = "Cross out that square.",
            )
        },
    )

    /** The variants of [technique], in intro order. */
    fun variants(technique: TechniqueId): List<IntroVariant> =
        if (technique == lineLock) {
            listOf(fillLine, fillRegion)
        } else {
            listOf(single.getValue(technique)(QueensTrainerCatalog.name(technique)))
        }
}
