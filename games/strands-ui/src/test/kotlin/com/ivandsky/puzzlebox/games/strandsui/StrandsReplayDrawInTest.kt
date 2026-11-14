package com.ivandsky.puzzlebox.games.strandsui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** F22: the pure draw-in timeline of a submitted word's path on the replay board. */
class StrandsReplayDrawInTest {
    private val letters = 4

    @Test
    fun `nothing is drawn at the start and every letter at the end`() {
        assertEquals(0, ReplayDrawIn.lettersAt(0, letters))
        assertEquals(letters, ReplayDrawIn.lettersAt(ReplayDrawIn.totalMs(letters), letters))
        assertEquals(letters, ReplayDrawIn.lettersAt(10_000, letters))
    }

    @Test
    fun `half the letters are drawn at the halfway point`() {
        assertEquals(letters / 2, ReplayDrawIn.lettersAt(ReplayDrawIn.totalMs(letters) / 2, letters))
    }

    @Test
    fun `the letters grow one at a time`() {
        val step = StrandsMotion.ReplayDrawInLetterMs
        assertEquals(listOf(1, 2, 3, 4), (1..letters).map { ReplayDrawIn.lettersAt(it * step, letters) })
    }

    @Test
    fun `a letter takes about 80 to 100 ms`() {
        assertTrue(StrandsMotion.ReplayDrawInLetterMs in 80..100)
    }

    @Test
    fun `what follows the draw-in starts only after it`() {
        val total = ReplayDrawIn.totalMs(letters)
        assertFalse(ReplayDrawIn.doneAt(total - 1, letters))
        assertTrue(ReplayDrawIn.doneAt(total, letters))
    }
}
