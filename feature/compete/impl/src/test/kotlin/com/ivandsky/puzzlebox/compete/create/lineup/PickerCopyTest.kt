package com.ivandsky.puzzlebox.compete.create.lineup

import com.ivandsky.puzzlebox.compete.create.QUEENS
import com.ivandsky.puzzlebox.domain.lineup.Freshness
import com.ivandsky.puzzlebox.domain.lineup.PuzzleSource
import org.junit.Assert.assertEquals
import org.junit.Test

/** The words of the exact-puzzle search that depend on what the player asked. */
class PickerCopyTest {

    @Test
    fun `each source reads as the label of a result row, a friend by name`() {
        assertEquals("Community", pickable(QUEENS, "K7Q2MX", "t", "Cy", PuzzleSource.Community).entry.sourceLabel())
        assertEquals("From Bo", CROWN_JEWELS.entry.sourceLabel())
        assertEquals("From a friend", pickable(QUEENS, "K7Q2MX", "t", null, PuzzleSource.Friend).entry.sourceLabel())
        assertEquals("Past day", pickable(QUEENS, "K7Q2MX", "t", null, PuzzleSource.PastDay).entry.sourceLabel())
        assertEquals("Yours", MY_GRID.entry.sourceLabel())
    }

    @Test
    fun `the line counts puzzles and keeps the order after the count until something narrows it`() {
        assertEquals("12 puzzles · Newest first", resultsLine(NO_QUERY, 12))
        assertEquals("1 puzzle · Newest first", resultsLine(NO_QUERY, 1))
        assertEquals("5 puzzles · Newest first", resultsLine(NO_QUERY.copy(text = "  "), 5))
        assertEquals("3 puzzles", resultsLine(NO_QUERY.copy(text = "cr"), 3))
        assertEquals("1 puzzle", resultsLine(NO_QUERY.copy(fresh = setOf(Freshness.New)), 1))
        assertEquals("0 puzzles", resultsLine(NO_QUERY.copy(sources = setOf(PuzzleSource.Mine)), 0))
    }

    @Test
    fun `an empty result names the text and adds with these filters only when chips narrow it`() {
        assertEquals("Nothing matches \"zzz\". Try a code, or clear the filters.", emptyLine(NO_QUERY.copy(text = "zzz")))
        assertEquals(
            "Nothing matches \"zzz\" with these filters. Try a code, or clear the filters.",
            emptyLine(NO_QUERY.copy(text = " zzz ", games = setOf(QUEENS))),
        )
        assertEquals("Nothing matches these filters. Try a code, or clear the filters.", emptyLine(NO_QUERY.copy(sources = setOf(PuzzleSource.PastDay))))
    }
}
