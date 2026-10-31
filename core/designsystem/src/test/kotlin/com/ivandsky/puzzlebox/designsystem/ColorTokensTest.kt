package com.ivandsky.puzzlebox.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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

    private fun contrast(foreground: Color, background: Color): Float {
        val top = foreground.luminance() + 0.05f
        val bottom = background.luminance() + 0.05f
        return if (top > bottom) top / bottom else bottom / top
    }
}
