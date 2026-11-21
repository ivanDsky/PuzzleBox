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
            "No line may hold three suns or three moons in a row. When two of the same kind sit side by side, " +
                "the squares at both ends of them must be the other kind.",
            "Two suns or two moons side by side, with an empty square next to them.",
            "Put the other kind in the empty square.",
        ),
        TechniqueId("sandwich") to Triple(
            "No line may hold three suns or three moons in a row. When two of the same kind have one empty square " +
                "between them, that square must be the other kind.",
            "Two squares of the same kind with one empty square between them.",
            "Put the other kind in the gap.",
        ),
        TechniqueId("full-line") to Triple(
            "Every row and column holds the same number of suns and moons. Once a line has all its suns, " +
                "every empty square left in it is a moon, and the other way round.",
            "A row or column that already has all its suns or all its moons.",
            "Fill the empty squares of that line with the other kind.",
        ),
        TechniqueId("equal-pair") to Triple(
            "Two empty squares joined by = hold the same kind. When a filled square sits right beside the pair, " +
                "matching it would make three in a row, so both squares take the other kind.",
            "An = sign between two empty squares, with a filled square right beside them.",
            "Put the other kind in both squares.",
        ),
        TechniqueId("sign-count") to Triple(
            "A × sign holds one sun and one moon, so it counts as one of each in its line. Count the signs and the " +
                "filled squares together: once one kind is used up, every other empty square takes the other kind.",
            "A line with signs where counting leaves only one kind to place.",
            "Fill the empty squares that are left with the kind that remains.",
        ),
        TechniqueId("split-pair") to Triple(
            "Two empty squares with a sun on one side and a moon on the other hold one of each, because two of the " +
                "same kind would make three in a row next to one end. Count that pair as one sun and one moon: once " +
                "a kind is used up, the other empty squares take the other kind.",
            "A sun, two empty squares, then a moon, in one line.",
            "Fill the other empty squares of that line with the kind that remains.",
        ),
        TechniqueId("count-squeeze") to Triple(
            "Try a kind in one empty square and see what the line then needs. If that kind would break the line, with " +
                "three in a row or a broken sign, or would use up the count and force the rest into a kind that cannot fit, " +
                "the square holds the other kind.",
            "A line with few empty squares where one guess would break it.",
            "Put the other kind in that square.",
        ),
        TechniqueId("line-options") to Triple(
            "List every way the line can still be filled without breaking a rule. A square that holds the same kind " +
                "in every way is settled.",
            "A line with few empty squares and only a couple of ways to fill it.",
            "Put the kind every way agrees on in that square.",
        ),
    )

    /** The variants of [technique], one; a technique with no copy here fails on first use, so a new one cannot ship unwritten. */
    fun of(technique: TechniqueId): List<IntroVariant> {
        val (rule, spotIt, doIt) = rules.getValue(technique)
        return listOf(IntroVariant(TangoTrainerCatalog.name(technique), rule, spotIt, doIt))
    }
}
