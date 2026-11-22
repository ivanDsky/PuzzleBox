package com.ivandsky.puzzlebox.games.tangoui.trainer

import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import com.ivandsky.puzzlebox.gameui.trainer.IntroVariant

/**
 * What each Tango technique's intro writes down (spec §2.7). Tango has no regions: a row and a column are one kind of
 * line, and every rung's steps read one kind of cell set, so each technique has one variant, titled with its catalog
 * name. The words follow the hint's own sentences ([com.ivandsky.puzzlebox.games.tangoui.hint.TangoStepCopy]) in
 * plain language: the board's two kinds are the sun and the moon, a line is a row or a column.
 */
internal object TangoIntroVariants {

    private val rules: Map<TechniqueId, Triple<String, String, String>> = mapOf(
        TechniqueId("sign") to Triple(
            "An = sign joins two squares that hold the same kind. A × sign joins two squares that hold different kinds. " +
                "When one of the two is filled in, the sign tells you the other.",
            "A sign with a filled square on one side and an empty square on the other.",
            "Put the same kind in the empty square for =, and the other kind for ×.",
        ),
        TechniqueId("pair") to Triple(
            "No row or column (a line) may hold three suns or three moons in a row. When two of the same kind sit " +
                "side by side, the squares at both ends of them must be the other kind.",
            "Two suns or two moons side by side, with an empty square at an end, in the same row or column.",
            "Put the other kind in each empty end.",
        ),
        TechniqueId("sandwich") to Triple(
            "No line may hold three suns or three moons in a row. When two of the same kind have one empty square " +
                "between them, that square must be the other kind.",
            "Two squares of the same kind with one empty square between them.",
            "Put the other kind in the gap.",
        ),
        TechniqueId("full-line") to Triple(
            "On this 6×6 board every row and column holds 3 suns and 3 moons. Once a line has 3 suns, " +
                "every empty square left in it is a moon, and the other way round.",
            "A row or column that already has all its suns or all its moons.",
            "Fill the empty squares of that line with the other kind.",
        ),
        TechniqueId("equal-pair") to Triple(
            "Two empty squares joined by = hold the same kind. When a filled square sits right at one end of the pair, " +
                "in the same row or column, matching it would make three in a row, so both squares take the other kind.",
            "An = sign between two empty squares, with a filled square right at one end of the pair, in the same row or column.",
            "Put the other kind in both squares.",
        ),
        TechniqueId("sign-count") to Triple(
            "Count each × that joins two empty squares in the line as one sun and one moon, then add the filled squares: " +
                "once one kind is used up, every other empty square takes the other kind. An = sign between two empty " +
                "squares needs two of the same kind, so if only one of a kind is left in the line, both take the other kind.",
            "A line with an empty × pair or = pair, where counting leaves one kind short.",
            "Fill the empty squares that are left with the kind that remains. For an = pair, put the other kind in both.",
        ),
        TechniqueId("split-pair") to Triple(
            "Two empty squares with a sun on one side and a moon on the other hold one of each, because two of the " +
                "same kind would make three in a row next to one end. Count that pair as one sun and one moon: once " +
                "a kind is used up, the other empty squares take the other kind.",
            "A sun and a moon in one line with exactly two empty squares between them, either way round.",
            "Fill the other empty squares of that line with the kind that remains.",
        ),
        TechniqueId("count-squeeze") to Triple(
            "Try one kind in an empty square. If that gives the line all 3 of that kind, every other empty square " +
                "must be the other kind. If that would make three in a row or break a sign, the guess was wrong: " +
                "the square holds the other kind.",
            "A line one short of all 3 suns (or moons), with a few empty squares.",
            "Put the kind you did not try in that square.",
        ),
        TechniqueId("line-options") to Triple(
            "List every way to fill the line that keeps its own rules: 3 of each, no three in a row, and the signs " +
                "inside it. A square that holds the same kind in every way is settled.",
            "A line with few empty squares and only a couple of ways to fill it.",
            "Put the kind every way agrees on in each such square.",
        ),
    )

    /** The variants of [technique], one; a technique with no copy here fails on first use, so a new one cannot ship unwritten. */
    fun of(technique: TechniqueId): List<IntroVariant> {
        val (rule, spotIt, doIt) = rules.getValue(technique)
        return listOf(IntroVariant(TangoTrainerCatalog.name(technique), rule, spotIt, doIt))
    }
}
