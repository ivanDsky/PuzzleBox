package com.ivandsky.puzzlebox.create.editor

import org.junit.Assert.assertEquals
import org.junit.Test

/** The size popover's range: up to four sizes in full, five or more as first to last. */
class SizeRangeTextTest {

    @Test
    fun `one size is itself`() {
        assertEquals("6×6", sizeRangeText(listOf("6×6")))
    }

    @Test
    fun `two sizes read as either`() {
        assertEquals("6×6 or 8×8", sizeRangeText(listOf("6×6", "8×8")))
    }

    @Test
    fun `four sizes are listed in full`() {
        assertEquals("4×4, 6×6, 8×8 or 10×10", sizeRangeText(listOf("4×4", "6×6", "8×8", "10×10")))
    }

    @Test
    fun `nine sizes read as a range`() {
        assertEquals("4×4 to 12×12", sizeRangeText((4..12).map { "$it×$it" }))
    }
}
