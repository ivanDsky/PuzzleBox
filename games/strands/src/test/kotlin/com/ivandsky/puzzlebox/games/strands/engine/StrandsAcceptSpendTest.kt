package com.ivandsky.puzzlebox.games.strands.engine

import com.ivandsky.puzzlebox.games.strands.ActiveHint
import com.ivandsky.puzzlebox.games.strands.HintStage
import com.ivandsky.puzzlebox.games.strands.StrandsState
import com.ivandsky.puzzlebox.games.strands.tinyPuzzle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StrandsAcceptSpendTest {

    private val fresh = StrandsState(tinyPuzzle)
    private val cat = tinyPuzzle.themeWords.single()

    @Test
    fun `spend shows the hint and adds exactly one`() {
        val hint = ActiveHint("CAT", HintStage.Outlined)
        val spent = fresh.copy(hintsUsed = 4).spend(StrandsHint.Reveal(hint))
        assertEquals(5, spent.hintsUsed)
        assertEquals(hint, spent.activeHint)
    }

    @Test
    fun `finding the hinted word clears the hint`() {
        val hinted = fresh.copy(activeHint = ActiveHint("CAT", HintStage.OrderRevealed), hintsUsed = 1)
        val after = hinted.accept(StrandsVerdict.ThemeWord(cat))
        assertEquals(listOf("CAT"), after.found)
        assertNull(after.activeHint)
        assertEquals(1, after.hintsUsed)
    }

    @Test
    fun `finding another word keeps the hint`() {
        val hint = ActiveHint("CAT", HintStage.Outlined)
        val after = fresh.copy(activeHint = hint).accept(StrandsVerdict.Spangram(tinyPuzzle.spangram))
        assertEquals(listOf("ATS"), after.found)
        assertEquals(hint, after.activeHint)
    }

    @Test
    fun `non-theme words keep insertion order, rejections change nothing`() {
        val after = fresh.accept(StrandsVerdict.NonTheme("TAT")).accept(StrandsVerdict.NonTheme("CATS"))
        assertEquals(listOf("TAT", "CATS"), after.nonTheme.toList())
        assertEquals(after, after.accept(StrandsVerdict.NotInWordList))
        assertEquals(after, after.accept(StrandsVerdict.AlreadyFound))
    }
}
