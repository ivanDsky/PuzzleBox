package com.ivandsky.puzzlebox.games.queensui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Floor guards for the module-local N-region palette (spec A13 Candy ramps, A10 contrast):
// the exact ramps page 06 picked, parity alternation within each mode, and the computed
// on-ink clearing 4.5:1 on EVERY fill under both the app's and Material's ink.
class QueensRegionPaletteTest {

    private fun contrast(fg: Color, bg: Color) = QueensRegionPalette.contrast(fg, bg)

    private fun lightFill(i: Int) = QueensRegionPalette.fill(i, dark = false)
    private fun darkFill(i: Int) = QueensRegionPalette.fill(i, dark = true)
    private fun lStar(c: Color) = QueensRegionPalette.cielL(c)
    private fun hex(c: Color) = "#%06X".format(c.toArgb() and 0xFFFFFF)

    @Test
    fun `light fills are the Candy ramp at OKLCH L 0_80 and 0_88`() {
        val candy = listOf(
            "#FFA196", "#FECCAA", "#DEBA50", "#ACEAAD", "#37D8C9",
            "#89E8FF", "#91C1FF", "#CFD4FF", "#D1A9FF", "#FFC3E3",
        )
        assertEquals(candy, List(10) { hex(lightFill(it)) })
    }

    @Test
    fun `dark fills are the Candy ramp at OKLCH L 0_36 and 0_44`() {
        val candy = listOf(
            "#662520", "#76441F", "#4C3B01", "#325F34", "#014841",
            "#025D6D", "#153D6D", "#4A4C80", "#492E64", "#733E5D",
        )
        assertEquals(candy, List(10) { hex(darkFill(it)) })
    }

    @Test
    fun `odd ids sit above even ids so parity neighbours always differ in lightness`() {
        for (even in listOf(0, 2, 4, 6, 8)) for (odd in listOf(1, 3, 5, 7, 9)) {
            val light = lStar(lightFill(odd)) - lStar(lightFill(even))
            assertTrue("light $even/$odd ΔL*=$light", light >= MIN_PARITY_DELTA_L)
            val dark = lStar(darkFill(odd)) - lStar(darkFill(even))
            assertTrue("dark $even/$odd ΔL*=$dark", dark >= MIN_PARITY_DELTA_L)
        }
    }

    @Test
    fun `on-ink clears 4_5 to 1 on every fill in both modes`() {
        // The app scheme's onSurface (identity tokens) and Material's baseline, each
        // against the scrim: the floor must hold whichever ink the scheme hands in.
        val lightInks = listOf(Color(0xFF33363D), Color(0xFF1C1B1F))
        val darkInks = listOf(Color(0xFFDBDEE2), Color(0xFFE6E1E5))
        for (i in 0 until 10) {
            for (onSurface in lightInks) {
                val ink = QueensRegionPalette.onInkFor(lightFill(i), onSurface, Color.Black)
                assertTrue("light[$i] ink ${contrast(ink, lightFill(i))}", contrast(ink, lightFill(i)) >= 4.5f)
            }
            for (onSurface in darkInks) {
                val ink = QueensRegionPalette.onInkFor(darkFill(i), onSurface, Color.Black)
                assertTrue("dark[$i] ink ${contrast(ink, darkFill(i))}", contrast(ink, darkFill(i)) >= 4.5f)
            }
        }
    }

    @Test
    fun `fills cycle deterministically by region id`() {
        assertEquals(lightFill(3), lightFill(13))
        assertEquals(lightFill(3), QueensRegionPalette.fill(3, dark = false))
    }

    @Test
    fun `on-ink computation picks the higher-contrast candidate`() {
        val onSurface = Color(0xFF1C1B1F)
        val scrim = Color.Black
        val fill = lightFill(0)
        val picked = QueensRegionPalette.onInkFor(fill, onSurface, scrim)
        val expected = if (contrast(onSurface, fill) >= contrast(scrim, fill)) onSurface else scrim
        assertEquals(expected, picked)
    }

    private companion object {
        // Candy's narrowest cross-parity gap (light teal 78.5 vs magenta 84.9) — the
        // saturated mid-tones trade the old 8-point floor for hue separation.
        const val MIN_PARITY_DELTA_L = 6f
    }
}
