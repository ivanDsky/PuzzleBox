package com.ivandsky.puzzlebox.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

class RollingDigitsTest {

    private val table = RecapMotion.OdometerLaps
    private val beyond = RecapMotion.OdometerLapsBeyond

    private fun lapsFor(value: Long): List<Int> {
        val digits = digitsOf(value)
        return digits.indices.map { lapsAt(digits.lastIndex - it, table, beyond) }
    }

    // Catches a digit split that reads the number back to front or drops a place.
    @Test
    fun `digits run most significant first`() {
        assertEquals(listOf(7), digitsOf(7))
        assertEquals(listOf(7, 0), digitsOf(70))
        assertEquals(listOf(3, 6, 5), digitsOf(365))
        assertEquals(listOf(1, 2, 8, 4), digitsOf(1_284))
        assertEquals(listOf(0), digitsOf(0))
    }

    // Catches a negative count crashing the card with a minus sign that is not a digit.
    @Test
    fun `a negative value reads as zero`() {
        assertEquals(listOf(0), digitsOf(-5))
    }

    // Catches the lap table applied from the wrong end: ones must lap twice, tens and hundreds once.
    @Test
    fun `laps by place give ones two, tens and hundreds one, the rest none`() {
        assertEquals(listOf(2), lapsFor(7))
        assertEquals(listOf(1, 2), lapsFor(70))
        assertEquals(listOf(1, 1, 2), lapsFor(365))
        assertEquals(listOf(0, 1, 1, 2), lapsFor(1_284))
    }

    // Catches a caller's own lap table (Fastest passes its own) being ignored.
    @Test
    fun `a caller table overrides the default`() {
        assertEquals(3, lapsAt(0, listOf(3), 0))
        assertEquals(5, lapsAt(4, listOf(3), 5))
    }

    // Catches a roll that ends off target: the settled digit must be the target at progress 1, any laps.
    @Test
    fun `a column ends laps past its digit and lands on it`() {
        assertEquals(0f, odometerPosition(7, 2, 0f), 0f)
        assertEquals(27f, odometerPosition(7, 2, 1f), 0f)
        assertEquals(7, windowAt(odometerPosition(7, 2, 1f)).first)
        assertEquals(0f, windowAt(odometerPosition(7, 2, 1f)).second, 0f)
        assertEquals(5, windowAt(odometerPosition(5, 0, 1f)).first)
        assertEquals(0, windowAt(odometerPosition(0, 1, 1f)).first)
    }

    // Catches a roll that jumps a whole digit: mid-roll the window shows the digit and how far it has left.
    @Test
    fun `mid roll the window carries a fraction between two digits`() {
        val (digit, scrolled) = windowAt(12.25f)
        assertEquals(2, digit)
        assertEquals(0.25f, scrolled, 1e-6f)
    }

    // Catches the stagger running right to left: the left column must start first.
    @Test
    fun `the left column starts first and each next one a stagger later`() {
        assertEquals(200, columnStartMs(0, 200, 60))
        assertEquals(260, columnStartMs(1, 200, 60))
        assertEquals(380, columnStartMs(3, 200, 60))
    }
}
