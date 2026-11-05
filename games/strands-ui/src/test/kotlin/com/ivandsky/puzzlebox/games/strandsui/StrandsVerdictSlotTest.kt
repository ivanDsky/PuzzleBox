package com.ivandsky.puzzlebox.games.strandsui

import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.StrandsWord
import com.ivandsky.puzzlebox.games.strands.engine.StrandsVerdict
import org.junit.Assert.assertEquals
import org.junit.Test

/** A3's copy for every verdict: found words show themselves, the rest name the problem. */
class StrandsVerdictSlotTest {

    private val word = StrandsWord("PIES", listOf(Coord(0, 0), Coord(0, 1), Coord(0, 2), Coord(0, 3)))

    private fun slot(verdict: StrandsVerdict) = StrandsVerdictSlot.of(verdict, seq = 7)

    @Test
    fun `rejections read their A3 copy`() {
        val expected = mapOf(
            StrandsVerdict.Empty to "Enter a word to submit",
            StrandsVerdict.TooShort to "Too short",
            StrandsVerdict.TooLong to "Too long",
            StrandsVerdict.AlreadyFound to "Already found",
            StrandsVerdict.WordLimitReached to "Word limit reached",
            StrandsVerdict.NotInWordList to "Not in word list",
            StrandsVerdict.InvalidPath to "Not in word list",
            StrandsVerdict.Foreign to "Not in word list",
        )
        for ((verdict, copy) in expected) {
            assertEquals(WordSlot.Verdict(copy, VerdictKind.Reject, 7), slot(verdict))
        }
    }

    @Test
    fun `a theme word shows the word`() {
        assertEquals(WordSlot.Verdict("PIES", VerdictKind.Theme, 7), slot(StrandsVerdict.ThemeWord(word)))
    }

    @Test
    fun `the spangram is announced`() {
        assertEquals(WordSlot.Verdict("Spangram!", VerdictKind.Spangram, 7), slot(StrandsVerdict.Spangram(word)))
    }

    @Test
    fun `a non-theme word shows the word`() {
        assertEquals(WordSlot.Verdict("PIKE", VerdictKind.NonTheme, 7), slot(StrandsVerdict.NonTheme("PIKE")))
    }

    @Test
    fun `the sequence number passes through, so a repeat verdict still retriggers`() {
        assertEquals(3, StrandsVerdictSlot.of(StrandsVerdict.TooShort, 3).seq)
    }
}
