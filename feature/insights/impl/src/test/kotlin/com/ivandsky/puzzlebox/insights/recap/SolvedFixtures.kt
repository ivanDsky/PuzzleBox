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

    val noHintFree = default.copy(hintFree = 0, longestHintFreeRun = null)

    /** A run the period has no single stretch to name. */
    val singleRun = default.copy(longestHintFreeRun = null)
}
