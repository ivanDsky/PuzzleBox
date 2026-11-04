package com.ivandsky.puzzlebox.games.queensui

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Floor guards for the module-local N-region palette (direction doc §4/§9, mirroring
// BoardTokensTest): luminance bands with parity alternation, ΔL* ≥ 8 for any even/odd
// neighbor pair within a mode, and the computed on-ink clearing 4.5:1 on EVERY fill.
class QueensRegionPaletteTest {

    private fun contrast(fg: Color, bg: Color) = QueensRegionPalette.contrast(fg, bg)

    private fun lightFill(i: Int) = QueensRegionPalette.fill(i, dark = false)
    private fun darkFill(i: Int) = QueensRegionPalette.fill(i, dark = true)

    @Test
    fun `light fills sit in the upper band, dark fills in the lower band`() {
        for (i in 0 until 10) {
            assertTrue("light[$i] L*=${QueensRegionPalette.cielL(lightFill(i))}", QueensRegionPalette.cielL(lightFill(i)) in 80f..94f)
            assertTrue("dark[$i] L*=${QueensRegionPalette.cielL(darkFill(i))}", QueensRegionPalette.cielL(darkFill(i)) in 14f..30f)
        }
    }

    @Test
    fun `every even-odd neighbor pair clears the deltaL floor in both modes`() {
        for (even in listOf(0, 2, 4, 6, 8)) for (odd in listOf(1, 3, 5, 7, 9)) {
            assertTrue(
                "light $even/$odd ΔL=${Math.abs(QueensRegionPalette.cielL(lightFill(even)) - QueensRegionPalette.cielL(lightFill(odd)))}",
                Math.abs(QueensRegionPalette.cielL(lightFill(even)) - QueensRegionPalette.cielL(lightFill(odd))) >= 8f,
            )
            assertTrue(
                "dark $even/$odd",
                Math.abs(QueensRegionPalette.cielL(darkFill(even)) - QueensRegionPalette.cielL(darkFill(odd))) >= 8f,
            )
        }
    }

    @Test
    fun `on-ink clears 4_5 to 1 on every fill in both modes`() {
        val lightOnSurface = Color(0xFF1C1B1F) // light scheme onSurface constant
        val lightScrim = Color.Black
        val darkOnSurface = Color(0xFFE6E1E5) // dark scheme onSurface constant
        val darkScrim = Color.Black
        for (i in 0 until 10) {
            val lightInk = QueensRegionPalette.onInkFor(lightFill(i), lightOnSurface, lightScrim)
            assertTrue("light[$i] ink ${contrast(lightInk, lightFill(i))}", contrast(lightInk, lightFill(i)) >= 4.5f)
            val darkInk = QueensRegionPalette.onInkFor(darkFill(i), darkOnSurface, darkScrim)
            assertTrue("dark[$i] ink ${contrast(darkInk, darkFill(i))}", contrast(darkInk, darkFill(i)) >= 4.5f)
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
}
