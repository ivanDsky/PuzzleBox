package com.ivandsky.puzzlebox.insights.recap

import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.designsystem.GameGlyphs
import com.ivandsky.puzzlebox.gameui.GameBrand
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.model.GameId

/** The Solved card's five contract states, over the five games a month can hold, each in its own brand colour. */
internal object SolvedFixtures {

    private val queens = GameId("queens")
    private val tango = GameId("tango")
    private val zip = GameId("zip")
    private val sudoku = GameId("sudoku")
    private val strands = GameId("strands")

    private val names = mapOf(queens to "Queens", tango to "Tango", zip to "Zip", sudoku to "Mini Sudoku", strands to "Strands")
    private val accents = mapOf(zip to AccentSlot.Blue, tango to AccentSlot.Orange, queens to AccentSlot.Violet, sudoku to AccentSlot.Teal, strands to AccentSlot.Pink)

    val gameName: (GameId) -> String = { names.getValue(it) }
    /** What each game's own UI module registers: its accent and its real glyph. */
    private val glyphs = mapOf(zip to GameGlyphs.Zip, tango to GameGlyphs.Tango, queens to GameGlyphs.Queens, sudoku to GameGlyphs.Sudoku, strands to GameGlyphs.Strands)
    val brands: (GameId) -> GameBrand = { GameBrand(accents.getValue(it), glyphs.getValue(it)) }

    private fun games(vararg solves: Int) = listOf(queens, tango, zip, sudoku, strands).zip(solves.toList()).map { (game, n) -> RecapCard.Solved.PerGame(game, n) }

    /** 96 solves, 71 without a hint, a longest run of 12; Strands' 8 is shorter than a band can be. */
    val default = RecapCard.Solved(96, games(28, 24, 22, 14, 8), hintFree = 71, longestHintFreeRun = 12)

    /** One solve, and none without a hint: no stripe. */
    val empty = RecapCard.Solved(1, games(1))

    /** Every solve without a hint, five games with 31 each. */
    val longest = RecapCard.Solved(155, games(31, 31, 31, 31, 31), hintFree = 155, longestHintFreeRun = 31)

    /** A year: 1067 solves over the five games; Strands' 6 against Queens' 334 is the narrowest tile the share hand draws. */
    val year = RecapCard.Solved(1067, games(334, 297, 288, 142, 6), hintFree = 902, longestHintFreeRun = 41)

    /** The user's 2025 in the prototype's order (Zip first): 1068 solves, 1061 without a hint, a run of 584; Strands' 6 is the thinnest band. */
    val year2025 = RecapCard.Solved(1068, inOrder(288, 297, 335, 142, 6), hintFree = 1061, longestHintFreeRun = 584)

    /** Five digits: 12345 solves over three and a half years, shown under the longest period line, [hugePeriod]. */
    val huge = RecapCard.Solved(12345, inOrder(3402, 2987, 4120, 1825, 11), hintFree = 11980, longestHintFreeRun = 2140)
    const val hugePeriod = "From 14 March 2023 to 21 November 2026"

    private fun inOrder(vararg solves: Int) = listOf(zip, tango, queens, sudoku, strands).zip(solves.toList()).map { (game, n) -> RecapCard.Solved.PerGame(game, n) }

    /** Two games: Queens and Zip. */
    val twoGames = RecapCard.Solved(58, listOf(RecapCard.Solved.PerGame(queens, 40), RecapCard.Solved.PerGame(zip, 18)), hintFree = 49, longestHintFreeRun = 9)

    val noHintFree = default.copy(hintFree = 0, longestHintFreeRun = null)

    /** A run the period has no single stretch to name. */
    val singleRun = default.copy(longestHintFreeRun = null)
}
