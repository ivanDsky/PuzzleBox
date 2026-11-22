package com.ivandsky.puzzlebox.games.tangoui.trainer

import com.ivandsky.puzzlebox.game.trainer.TechniqueId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The words of each Tango technique intro, pinned where the new-user review found them wrong or unclear. */
class TangoIntroVariantsTest {

    private fun variant(id: String) = TangoIntroVariants.of(TechniqueId(id)).single()

    @Test
    fun `the first use of line says it is a row or a column`() {
        assertTrue(variant("pair").rule.contains("No row or column (a line) may hold three suns or three moons in a row."))
    }

    @Test
    fun `pair looks for an end in the same line, and fills each empty end`() {
        val pair = variant("pair")
        assertTrue(pair.spotIt, pair.spotIt == "Two suns or two moons side by side, with an empty square at an end, in the same row or column.")
        assertTrue(pair.doIt, pair.doIt == "Put the other kind in each empty end.")
    }

    @Test
    fun `full line gives the number a 6 by 6 board holds`() {
        assertTrue(
            variant("full-line").rule.startsWith(
                "On this 6×6 board every row and column holds 3 suns and 3 moons. Once a line has 3 suns, " +
                    "every empty square left in it is a moon, and the other way round.",
            ),
        )
    }

    @Test
    fun `equal pair needs the filled square at an end of the pair, in the same line`() {
        val equal = variant("equal-pair")
        assertTrue(equal.rule, "right at one end of the pair, in the same row or column" in equal.rule)
        assertTrue(equal.spotIt, "right at one end of the pair, in the same row or column" in equal.spotIt)
        assertFalse(equal.rule, "right beside" in equal.rule)
    }

    @Test
    fun `sign count teaches the x pair and the = pair`() {
        val count = variant("sign-count")
        assertTrue(count.rule, "Count each × that joins two empty squares in the line as one sun and one moon, then add the filled squares" in count.rule)
        assertTrue(
            count.rule,
            "An = sign between two empty squares needs two of the same kind, so if only one of a kind is left in the line, both take the other kind." in count.rule,
        )
        assertTrue(count.spotIt, count.spotIt == "A line with an empty × pair or = pair, where counting leaves one kind short.")
    }

    @Test
    fun `split pair works with the sun or the moon first`() {
        assertTrue(
            variant("split-pair").spotIt ==
                "A sun and a moon in one line with exactly two empty squares between them, either way round.",
        )
    }

    @Test
    fun `count squeeze teaches the guess that uses up the count, and the wrong guess`() {
        val squeeze = variant("count-squeeze")
        assertTrue(
            squeeze.rule,
            squeeze.rule == "Try one kind in an empty square. If that gives the line all 3 of that kind, every other empty square " +
                "must be the other kind. If that would make three in a row or break a sign, the guess was wrong: " +
                "the square holds the other kind.",
        )
        assertTrue(squeeze.spotIt, squeeze.spotIt == "A line one short of all 3 suns (or moons), with a few empty squares.")
        assertTrue(squeeze.doIt, squeeze.doIt == "Put the kind you did not try in that square.")
    }

    @Test
    fun `line options counts only the rules inside the line, and fills each settled square`() {
        val options = variant("line-options")
        assertTrue(
            options.rule,
            options.rule.startsWith("List every way to fill the line that keeps its own rules: 3 of each, no three in a row, and the signs inside it."),
        )
        assertTrue(options.doIt, options.doIt == "Put the kind every way agrees on in each such square.")
    }
}
