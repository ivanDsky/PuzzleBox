package com.ivandsky.puzzlebox.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorTokensTest {

    @Test
    fun `dark and light schemes are distinct`() {
        assertNotEquals(LightColorScheme.primary, DarkColorScheme.primary)
        assertNotEquals(LightColorScheme.surface, DarkColorScheme.surface)
        assertNotEquals(LightColorScheme.onSurface, DarkColorScheme.onSurface)
    }

    @Test
    fun `text roles meet WCAG AA contrast in both themes`() {
        // The shipped pair (ticket 10): C3 light, C6 dark.
        for (scheme in listOf(LightColorScheme, DarkColorScheme)) {
            assertTrue(contrast(scheme.onSurface, scheme.surface) >= 4.5f)
            assertTrue(contrast(scheme.onSurfaceVariant, scheme.surfaceVariant) >= 4.5f)
            assertTrue(contrast(scheme.onPrimary, scheme.primary) >= 4.5f)
            assertTrue(contrast(scheme.onPrimaryContainer, scheme.primaryContainer) >= 4.5f)
        }
    }

    @Test
    fun `container pairs used by components meet WCAG AA contrast in both themes`() {
        // Chips (secondary/tertiary containers), states and error surfaces, and the
        // board cell background — every container/on pair the component set leans on.
        for (scheme in listOf(LightColorScheme, DarkColorScheme)) {
            assertTrue(contrast(scheme.onSecondaryContainer, scheme.secondaryContainer) >= 4.5f)
            assertTrue(contrast(scheme.onTertiaryContainer, scheme.tertiaryContainer) >= 4.5f)
            assertTrue(contrast(scheme.onErrorContainer, scheme.errorContainer) >= 4.5f)
            assertTrue(contrast(scheme.onSurface, scheme.surfaceContainerLowest) >= 4.5f)
            assertTrue(contrast(scheme.onSurface, scheme.surfaceContainerLow) >= 4.5f)
            assertTrue(contrast(scheme.onSurfaceVariant, scheme.surfaceContainerLow) >= 4.5f)
        }
    }

    @Test
    fun `primary-as-text role meets WCAG AA on every surface it renders on`() {
        // AppTextButton renders the primary hue as text (AppTextButton.kt). The light
        // value is the darker accessible step; dark keeps colorScheme.primary itself,
        // which already clears AA. Surfaces: white (surface + lowest container), the
        // Home cards, and the dialog surface (surfaceContainerHigh).
        val lightSurfaces = listOf(
            Color.White,
            LightColorScheme.surfaceContainerLow,
            LightColorScheme.surfaceContainer,
            LightColorScheme.surfaceContainerHigh,
        )
        for (surface in lightSurfaces) {
            assertTrue(contrast(LightPrimaryText, surface) >= 4.5f)
        }
        for (surface in listOf(DarkColorScheme.surface, DarkColorScheme.surfaceContainerHigh)) {
            assertTrue(contrast(DarkPrimaryText, surface) >= 4.5f)
        }
    }

    @Test
    fun `warning role clears text and border contrast on every surface and is not the error`() {
        // Border >= 3:1 (non-text), line text >= 4.5:1. Light tertiary (#FF9600) is 2.18:1 on
        // white, so light carries its own deeper step; dark reuses tertiary's amber.
        listOf(LightWarning to LightColorScheme, DarkWarning to DarkColorScheme).forEach { (warning, scheme) ->
            val surfaces = listOf(
                scheme.background, scheme.surface, scheme.surfaceContainerLowest, scheme.surfaceContainerLow,
                scheme.surfaceContainer, scheme.surfaceContainerHigh, scheme.surfaceContainerHighest,
            )
            for (surface in surfaces) {
                val ratio = contrast(warning, surface)
                assertTrue("$warning on $surface is $ratio:1, under 4.5:1", ratio >= 4.5f)
            }
            assertNotEquals(scheme.error, warning)
        }
        assertEquals(DarkColorScheme.tertiary, DarkWarning)
    }

    @Test
    fun `surface ramps carry no warm cast in both themes`() {
        // Ticket 11: the neutral family reads cool — every surface/outline value has
        // blue >= green >= red (the old light ramp was cream-green, green > blue).
        // The deepest dark layers additionally stay near-black slate with bounded
        // chroma so panels can't render them warm (the old windowBackground was
        // red-brown #1B110D; it now pairs with surface in app res colors).
        val lightNeutrals = listOf(
            LightColorScheme.surfaceVariant,
            LightColorScheme.onSurfaceVariant,
            LightColorScheme.outline,
            LightColorScheme.outlineVariant,
            LightColorScheme.surfaceDim,
            LightColorScheme.surfaceContainerLow,
            LightColorScheme.surfaceContainer,
            LightColorScheme.surfaceContainerHigh,
            LightColorScheme.surfaceContainerHighest,
        )
        val darkNeutrals = listOf(
            DarkColorScheme.surfaceVariant,
            DarkColorScheme.onSurfaceVariant,
            DarkColorScheme.outline,
            DarkColorScheme.outlineVariant,
            DarkColorScheme.surfaceBright,
            DarkColorScheme.surfaceContainerLow,
            DarkColorScheme.surfaceContainer,
            DarkColorScheme.surfaceContainerHigh,
            DarkColorScheme.surfaceContainerHighest,
        )
        for (c in lightNeutrals + darkNeutrals) {
            assertTrue("$c is not cool-neutral", c.blue >= c.green && c.green >= c.red)
        }
        for (c in listOf(DarkColorScheme.surface, DarkColorScheme.surfaceContainerLowest)) {
            assertTrue("$c is not near-black slate", c.red >= 8f / 255f && (c.blue - c.red) <= 10f / 255f)
        }
    }

    @Test
    fun `home card floats visibly above the pane in both themes`() {
        // Ticket 11: light cards are white on the cool-paper pane (the brief's
        // FFFFFF-on-F2F4F7 target); dark cards are the first container step on the
        // near-black surface. The ratios pin the visible step against future drift.
        assertTrue(contrast(LightColorScheme.surface, LightColorScheme.surfaceContainer) >= 1.10f)
        assertTrue(contrast(DarkColorScheme.surfaceContainerLow, DarkColorScheme.surface) >= 1.05f)
    }

    private fun contrast(foreground: Color, background: Color): Float {
        val top = foreground.luminance() + 0.05f
        val bottom = background.luminance() + 0.05f
        return if (top > bottom) top / bottom else bottom / top
    }
}
