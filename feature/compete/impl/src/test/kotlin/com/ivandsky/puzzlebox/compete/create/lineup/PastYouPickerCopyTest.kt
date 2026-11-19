package com.ivandsky.puzzlebox.compete.create.lineup

import com.ivandsky.puzzlebox.compete.create.QUEENS
import com.ivandsky.puzzlebox.domain.lineup.PuzzleSource
import org.junit.Assert.assertEquals
import org.junit.Test

/** The Past you add sheet's lines: what the order is, how many match, and why the list is empty. */
class PastYouPickerCopyTest {

    @Test
    fun `Past you counts puzzles and names its order until something narrows it`() {
        assertEquals("12 puzzles · Recently solved first", resultsLine(NO_QUERY, 12, pastYou = true))
        assertEquals("1 puzzle · Recently solved first", resultsLine(NO_QUERY, 1, pastYou = true))
        assertEquals("5 puzzles · Recently solved first", resultsLine(NO_QUERY.copy(text = "  "), 5, pastYou = true))
        assertEquals("3 puzzles", resultsLine(NO_QUERY.copy(text = "zip"), 3, pastYou = true))
        assertEquals("1 puzzle", resultsLine(NO_QUERY.copy(sources = setOf(PuzzleSource.PastDay)), 1, pastYou = true))
        assertEquals("2 puzzles", resultsLine(NO_QUERY.copy(games = setOf(QUEENS)), 2, pastYou = true))
    }

    @Test
    fun `nothing solved and nothing typed says so in plain words`() {
        assertEquals("You haven't solved any puzzles yet.", emptyLine(NO_QUERY, pastYou = true))
        assertEquals("You haven't solved any puzzles yet.", emptyLine(NO_QUERY.copy(text = " "), pastYou = true))
    }

    @Test
    fun `a narrowed empty list names the text and says Past you only has what you solved`() {
        assertEquals("Nothing matches \"zzz\". Past you only has puzzles you've solved.", emptyLine(NO_QUERY.copy(text = " zzz "), pastYou = true))
        assertEquals(
            "Nothing matches \"zzz\" with these filters. Past you only has puzzles you've solved.",
            emptyLine(NO_QUERY.copy(text = "zzz", games = setOf(QUEENS)), pastYou = true),
        )
        assertEquals("Nothing matches these filters. Past you only has puzzles you've solved.", emptyLine(NO_QUERY.copy(sources = setOf(PuzzleSource.Friend)), pastYou = true))
    }
}
