package com.ivandsky.puzzlebox.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The ink for small text on a full accent fill reads at 4.5:1 on every accent, light and dark. */
class ReadableInkTest {

    private val themes = listOf(
        Triple("light", LightAccentTokens, LightOnFill),
        Triple("dark", DarkAccentTokens, DarkOnFill),
    )

    @Test
    fun `ink on every accent fill reads at 4_5 in light and dark`() {
        for ((theme, accents, onFill) in themes) {
            for ((slot, accent) in accents) {
                val ink = readableInk(accent.accent, onFill)
                val ratio = wcag(ink, accent.accent)
                assertTrue("$theme $slot: $ink on ${accent.accent} is $ratio:1, under 4.5:1", ratio >= 4.5f)
            }
        }
    }

    @Test
    fun `the theme's own on-fill is kept wherever it already reads`() {
        // Light neutral is a dark fill under white; every dark accent is a bright fill under the dark ink.
        assertEquals(LightOnFill, readableInk(LightAccentTokens.getValue(AccentSlot.Neutral).accent, LightOnFill))
        DarkAccentTokens.values.forEach { assertEquals(DarkOnFill, readableInk(it.accent, DarkOnFill)) }
    }

    @Test
    fun `white is replaced by the dark ink on the bright light accents`() {
        for (slot in AccentSlot.entries - AccentSlot.Neutral) {
            val fill = LightAccentTokens.getValue(slot).accent
            assertTrue("white on $slot reads at ${wcag(LightOnFill, fill)}:1, so it must be replaced", wcag(LightOnFill, fill) < 4.5f)
            assertEquals(OnBrightFill, readableInk(fill, LightOnFill))
        }
    }

    // Independent of contrastRatio, so the helper under use is not its own oracle.
    private fun wcag(a: Color, b: Color): Float {
        val hi = maxOf(a.luminance(), b.luminance()) + 0.05f
        val lo = minOf(a.luminance(), b.luminance()) + 0.05f
        return hi / lo
    }
}
