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

    /** Index of Line lock's "Color inside one line" variant: a color's open squares all sit in one line. */
    const val FILL_LINE = 0

    /** Index of Line lock's "Line inside one color" variant: a line's open squares all belong to one color. */
    const val FILL_REGION = 1

    private val lineLock = TechniqueId("line-lock")

    private val fillLine = IntroVariant(
        title = "Color inside one line",
        rule = "All of a color's open squares sit in one row or column. Its queen must be in that line, so the rest of the line is out.",
        spotIt = "A color whose open squares are all in one row or column.",
        doIt = "Cross out the rest of that row or column.",
    )

    private val fillRegion = IntroVariant(
        title = "Line inside one color",
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
                rule = "A queen on some square would touch (even corner to corner) every open square of a row, column or color " +
                    "that still needs its queen. That leaves it no room, so the square is out.",
                spotIt = "A row, column or color whose open squares are all touched by one other square.",
                doIt = "Cross out every square like that.",
            )
        },
        TechniqueId("pigeonhole-regions") to { name ->
            IntroVariant(
                title = name,
                rule = "Two colors whose open squares all sit in the same two rows (or three colors in three rows) take those rows' " +
                    "queens, so the rows' other open squares are out. Also: two rows whose open squares are only two colors use up those colors.",
                spotIt = "Two or three colors whose open squares all sit in as many rows or columns, or the same with rows or columns " +
                    "and colors swapped.",
                doIt = "Cross out the other open squares of those rows or columns, or, the other way round, of those colors.",
            )
        },
        TechniqueId("pigeonhole-lines") to { name ->
            IntroVariant(
                title = name,
                rule = "Two rows whose open squares all sit in the same two columns take those columns' queens, so the columns' " +
                    "other open squares are out. The same works with columns and rows swapped.",
                spotIt = "Two or more rows whose open squares all sit in as many columns, or columns whose open squares sit in as many rows.",
                doIt = "Cross out the other open squares of those columns, or, the other way round, of those rows.",
            )
        },
        TechniqueId("leftover-queen") to { name ->
            IntroVariant(
                title = name,
                rule = "Take three rows. If two colors sit entirely inside them, those colors use two of the rows' three queens. " +
                    "The last queen must go on the rows' other open squares, so any square that would rule out all of those is out. " +
                    "It works the other way round too: if two rows sit entirely inside three colors, the last queen goes on the colors' other open squares.",
                spotIt = "A few rows or columns, or colors, with one more queen than the colors or lines sitting entirely inside them.",
                doIt = "Cross out every square that would rule out all of the squares left for that last queen.",
            )
        },
        TechniqueId("squeeze") to { name ->
            IntroVariant(
                title = name,
                rule = "A queen on some square would rule out every open square of a row, column or color, by touching them or by " +
                    "sharing a row, column or color with them. That leaves it no room, so the square is out.",
                spotIt = "A square whose queen would rule out all of that row, column or color's open squares.",
                doIt = "Cross out every square like that.",
            )
        },
        TechniqueId("count-squeeze") to { name ->
            IntroVariant(
                title = name,
                rule = "A queen on some square would leave two or more rows, columns or colors needing more queens than the lines " +
                    "they still reach. That is not enough room, so the square is out.",
                spotIt = "A square whose queen would leave two or more rows, columns or colors needing more queens than the lines they still reach.",
                doIt = "Cross out every square like that.",
            )
        },
        TechniqueId("short-look-ahead") to { name ->
            IntroVariant(
                title = name,
                rule = "Try a queen on a square, then make the moves it forces right away (every row, column or color left with one " +
                    "open square gets its queen). If that leaves some row, column or color, or a group of them, without room, " +
                    "the first square is out.",
                spotIt = "A square whose queen, with the moves it forces, leaves a row, column or color, or a group of them, no room.",
                doIt = "Cross out every square like that.",
            )
        },
        TechniqueId("long-look-ahead") to { name ->
            IntroVariant(
                title = name,
                rule = "Try a queen on a square and keep making the moves it forces. If, several moves later, a row, column or color " +
                    "has no room left, the first square is out.",
                spotIt = "A square whose queen sets off forced moves that, several moves later, leave a row, column or color no room.",
                doIt = "Cross out every square like that.",
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
