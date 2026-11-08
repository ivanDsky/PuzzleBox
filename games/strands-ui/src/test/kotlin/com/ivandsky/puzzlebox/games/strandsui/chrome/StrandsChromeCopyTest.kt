package com.ivandsky.puzzlebox.games.strandsui.chrome

import com.ivandsky.puzzlebox.games.strands.StrandsRating
import com.ivandsky.puzzlebox.games.strandsui.StrandsCopy
import org.junit.Assert.assertEquals
import org.junit.Test

/** The chrome's pure parts: A7 and A36 copy, the header line, the announcement and the hint state. */
class StrandsChromeCopyTest {

    @Test
    fun `both hint modals carry the A7 copy verbatim`() {
        assertEquals(
            "Your hint's on the board. Use your current hint to create a word before getting a new hint.",
            StrandsCopy.HINT_ON_BOARD,
        )
        assertEquals(
            "Need a hint? Find non-theme words to get hints. For every 3 non-theme words you find, you earn a hint.",
            StrandsCopy.HINT_NONE_AVAILABLE,
        )
    }

    @Test
    fun `the rating word follows hints used (A36)`() {
        assertEquals("Perfect!", StrandsCopy.rating(StrandsRating.of(0)))
        assertEquals("Great!", StrandsCopy.rating(StrandsRating.of(1)))
        assertEquals("Well done!", StrandsCopy.rating(StrandsRating.of(2)))
        assertEquals("Well done!", StrandsCopy.rating(StrandsRating.of(5)))
    }

    @Test
    fun `the header line joins the number and credit, and drops a missing number`() {
        assertEquals("No. 669 · by Tess Tester", StrandsCopy.headerLine(669, "by Tess Tester"))
        assertEquals("by Tess Tester", StrandsCopy.headerLine(null, "by Tess Tester"))
    }

    @Test
    fun `a verdict is announced with its copy, then the counter`() {
        assertEquals("Not in word list. 1 of 4 theme words found.", StrandsCopy.announcement("Not in word list", 1, 4))
        assertEquals("Spangram! 2 of 4 theme words found.", StrandsCopy.announcement("Spangram!", 2, 4))
    }

    @Test
    fun `the unavailable hint state names progress in thirds, never a used count`() {
        assertEquals("No hint available, 2 of 3 words toward the next hint", StrandsCopy.hintUnavailable(2))
    }

    @Test
    fun `an available hint reads its count first, then progress`() {
        assertEquals("1 hint available, 0 of 3 words toward the next hint", StrandsCopy.hintAvailable(1, 0))
        assertEquals("3 hints available, 2 of 3 words toward the next hint", StrandsCopy.hintAvailable(3, 2))
    }
}
