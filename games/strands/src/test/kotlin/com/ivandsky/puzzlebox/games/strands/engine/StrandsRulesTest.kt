package com.ivandsky.puzzlebox.games.strands.engine

import com.ivandsky.puzzlebox.game.Move
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.StrandsState
import com.ivandsky.puzzlebox.games.strands.tinyPuzzle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrandsRulesTest {

    private val fresh = StrandsState(tinyPuzzle)
    private val cat = tinyPuzzle.themeWords.single()

    private fun trace(vararg cells: Pair<Int, Int>) = StrandsMove(cells.map { Coord(it.first, it.second) })

    @Test
    fun `a theme word counts wherever it was traced`() {
        assertEquals(StrandsVerdict.ThemeWord(cat), StrandsRules.validate(trace(0 to 0, 0 to 1, 0 to 2), fresh))
        assertEquals(StrandsVerdict.ThemeWord(cat), StrandsRules.validate(trace(0 to 0, 1 to 0, 1 to 1), fresh))
    }

    @Test
    fun `the spangram is its own verdict`() {
        assertEquals(
            StrandsVerdict.Spangram(tinyPuzzle.spangram),
            StrandsRules.validate(trace(1 to 0, 1 to 1, 1 to 2), fresh),
        )
    }

    @Test
    fun `a found word is already found`() {
        val state = fresh.copy(found = listOf("CAT"), nonTheme = setOf("TAT"))
        assertEquals(StrandsVerdict.AlreadyFound, StrandsRules.validate(trace(0 to 0, 0 to 1, 0 to 2), state))
        assertEquals(StrandsVerdict.AlreadyFound, StrandsRules.validate(trace(0 to 2, 0 to 1, 1 to 1), state))
    }

    @Test
    fun `other valid words are non-theme, the rest not in the list`() {
        assertEquals(StrandsVerdict.NonTheme("TAT"), StrandsRules.validate(trace(0 to 2, 0 to 1, 1 to 1), fresh))
        assertEquals(StrandsVerdict.NotInWordList, StrandsRules.validate(trace(1 to 2, 0 to 1, 0 to 0), fresh))
    }

    @Test
    fun `guards come first`() {
        assertEquals(StrandsVerdict.Empty, StrandsRules.validate(StrandsMove(emptyList()), fresh))
        assertEquals(StrandsVerdict.InvalidPath, StrandsRules.validate(trace(0 to 0, 0 to 2), fresh))
        assertEquals(StrandsVerdict.InvalidPath, StrandsRules.validate(trace(0 to 0, 2 to 0), fresh))
        assertEquals(StrandsVerdict.Foreign, StrandsRules.validate(object : Move {}, fresh))
    }

    @Test
    fun `completion needs every theme word and the spangram`() {
        assertFalse(StrandsRules.isComplete(fresh.copy(found = listOf("CAT"))))
        assertFalse(StrandsRules.isComplete(fresh.copy(found = listOf("ATS"))))
        assertTrue(StrandsRules.isComplete(fresh.copy(found = listOf("ATS", "CAT"))))
    }

    @Test
    fun `the skeleton hint economy offers nothing`() {
        assertEquals(StrandsHint.NoneAvailable, StrandsRules.hint(fresh))
        assertEquals(0, StrandsHints.available(fresh))
        assertEquals(1, StrandsHints.meterThirds(fresh.copy(nonTheme = setOf("TAT"))))
    }
}
