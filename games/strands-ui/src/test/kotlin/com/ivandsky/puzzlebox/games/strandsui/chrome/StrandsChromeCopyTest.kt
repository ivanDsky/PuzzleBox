package com.ivandsky.puzzlebox.games.strandsui.chrome

import com.ivandsky.puzzlebox.games.strands.StrandsRating
import com.ivandsky.puzzlebox.games.strandsui.HintUi
import com.ivandsky.puzzlebox.games.strandsui.StrandsCopy
import com.ivandsky.puzzlebox.games.strandsui.StrandsMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The chrome's pure parts: A7 and A36 copy, the header line, the announcement, the meter and the shake curve. */
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
    fun `the meter fills in thirds and reads full while a hint waits`() {
        assertEquals(0f, hintMeterFraction(HintUi(available = false, meterThirds = 0)))
        assertEquals(1f / 3, hintMeterFraction(HintUi(available = false, meterThirds = 1)), 1e-6f)
        assertEquals(2f / 3, hintMeterFraction(HintUi(available = false, meterThirds = 2)), 1e-6f)
        assertEquals(1f, hintMeterFraction(HintUi(available = true, meterThirds = 1)))
    }

    @Test
    fun `the shake runs NYT's keyframes for one and a half cycles, then rests`() {
        val cycle = StrandsMotion.ShakeMs
        assertEquals(0f, StrandsMotion.shakeOffset(0))
        assertEquals(-1f, StrandsMotion.shakeOffset(cycle / 3 + 1), 0.05f)
        assertEquals(1f, StrandsMotion.shakeOffset(2 * cycle / 3 + 1), 0.05f)
        assertEquals(-1f, StrandsMotion.shakeOffset(cycle + cycle / 3 + 1), 0.05f)
        assertEquals(0f, StrandsMotion.shakeOffset(StrandsMotion.ShakeTotalMs))
        for (ms in 0..StrandsMotion.ShakeTotalMs) assertTrue(StrandsMotion.shakeOffset(ms) in -1f..1f)
    }
}
