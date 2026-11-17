package com.ivandsky.puzzlebox.gameui.hidden

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The blur of the path below Android 12: pure pixel maths, so it is pinned without a device. */
class HiddenBoardBoxBlurTest {

    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()

    @Test
    fun `a flat picture stays flat`() {
        val pixels = IntArray(12 * 9) { 0xFF336699.toInt() }
        HiddenBoardBoxBlur.blur(pixels, width = 12, height = 9, radius = 3)
        assertArrayEquals(IntArray(12 * 9) { 0xFF336699.toInt() }, pixels)
    }

    @Test
    fun `one bright cell is spread thin, never left readable`() {
        val side = 31
        val pixels = IntArray(side * side) { black }
        pixels[15 * side + 15] = white
        HiddenBoardBoxBlur.blur(pixels, side, side, radius = 4)
        val centre = pixels[15 * side + 15] and 0xFF
        assertTrue("centre $centre", centre in 1..12)
        // The light reaches well past one box width and fades with distance.
        val near = pixels[15 * side + 19] and 0xFF
        val far = pixels[15 * side + 24] and 0xFF
        assertTrue("near $near far $far centre $centre", centre >= near && near >= far)
        assertTrue("near $near", near > 0)
    }

    @Test
    fun `a hard edge becomes a ramp`() {
        val width = 40
        val pixels = IntArray(width * 4) { if (it % width < width / 2) black else white }
        HiddenBoardBoxBlur.blur(pixels, width, 4, radius = 3)
        val row = (0 until width).map { pixels[it] and 0xFF }
        assertEquals(row.sorted(), row)
        assertTrue("ramp ${row.distinct().size}", row.distinct().size >= 10)
        assertEquals(0, row.first())
        assertEquals(255, row.last())
    }

    @Test
    fun `clear pixels stay clear and do not darken their neighbours`() {
        val width = 24
        val pixels = IntArray(width * 3) { if (it % width < width / 2) 0 else white }
        HiddenBoardBoxBlur.blur(pixels, width, 3, radius = 2)
        assertEquals(0, pixels[0])
        // Half-covered pixels keep the white colour; only their coverage drops.
        val mid = pixels[width / 2]
        assertTrue("alpha ${mid ushr 24}", (mid ushr 24) in 1..254)
        assertTrue("red ${(mid shr 16) and 0xFF}", (mid shr 16) and 0xFF >= 250)
    }

    @Test
    fun `three passes of the box match the asked standard deviation`() {
        // Variance of n box passes of width w is n(w^2 - 1)/12.
        for (sigma in listOf(2f, 4f, 5.9f, 8f)) {
            val radius = HiddenBoardBoxBlur.radiusFor(sigma)
            val width = 2 * radius + 1
            val got = kotlin.math.sqrt(HiddenBoardBoxBlur.PASSES * (width * width - 1) / 12.0)
            assertEquals(sigma.toDouble(), got, 0.6)
        }
    }

    @Test
    fun `the picture is shrunk until its blur is a few pixels wide`() {
        assertEquals(1, HiddenBoardBoxBlur.shrinkFor(sigmaPx = 3f))
        assertEquals(4, HiddenBoardBoxBlur.shrinkFor(sigmaPx = 18f))
        assertEquals(11, HiddenBoardBoxBlur.shrinkFor(sigmaPx = 47.25f))
    }
}
