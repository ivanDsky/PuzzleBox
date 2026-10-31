package com.ivandsky.puzzlebox.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Resolver logic for the ticket 08 style prototypes. The control variants must resolve
 * to the exact shipped token objects (identity, not a copy) — that is what keeps the
 * default rendering path byte-identical under the committed goldens.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StyleVariantTest {

    @Test
    fun `default typography is the shipped editorial table`() {
        assertSame(AppTypography, TypographyVariant.DEFAULT.toTypography())
        assertSame(AppTypography, TypographyVariant.EDITORIAL.toTypography())
    }

    @Test
    fun `non-default typography variants build their own tables`() {
        for (variant in TypographyVariant.entries.filter { it != TypographyVariant.EDITORIAL }) {
            assertNotNull(variant.toTypography())
            assertNotSame(AppTypography, variant.toTypography())
        }
    }

    @Test
    fun `default palette follows dark mode with the shipped schemes`() {
        val palette = PaletteVariant.DEFAULT
        assertSame(LightColorScheme, palette.toColorScheme(darkTheme = false))
        assertSame(DarkColorScheme, palette.toColorScheme(darkTheme = true))
        assertTrue(palette.supportsDarkScheme())
    }

    @Test
    fun `only the editorial palette supports dark mode`() {
        for (variant in PaletteVariant.entries) {
            assertEquals(
                "$variant supportsDarkScheme",
                variant == PaletteVariant.EDITORIAL_DARK,
                variant.supportsDarkScheme(),
            )
        }
    }

    @Test
    fun `light-look palettes ignore the dark request`() {
        for (variant in listOf(PaletteVariant.CLEAN_LIGHT, PaletteVariant.PLAYFUL_SATURATED)) {
            assertSame(
                variant.toColorScheme(darkTheme = false),
                variant.toColorScheme(darkTheme = true),
            )
            assertFalse(variant.supportsDarkScheme())
        }
    }

    @Test
    fun `stored names round-trip every variant`() {
        for (variant in TypographyVariant.entries) {
            assertEquals(variant, TypographyVariant.fromStored(variant.name))
        }
        for (variant in PaletteVariant.entries) {
            assertEquals(variant, PaletteVariant.fromStored(variant.name))
        }
    }

    @Test
    fun `stored names of unknown or absent values fall back to null`() {
        assertNull(TypographyVariant.fromStored(null))
        assertNull(TypographyVariant.fromStored("SERIF"))
        assertNull(TypographyVariant.fromStored("editorial"))
        assertNull(PaletteVariant.fromStored(null))
        assertNull(PaletteVariant.fromStored("QUIZLET"))
        assertNull(PaletteVariant.fromStored("clean light"))
    }

    @Test
    fun `every variant exposes a non-blank label and blurb`() {
        for (variant in TypographyVariant.entries) {
            assertTrue("${variant.name} label", variant.label.isNotBlank())
            assertTrue("${variant.name} blurb", variant.blurb.isNotBlank())
        }
        for (variant in PaletteVariant.entries) {
            assertTrue("${variant.name} label", variant.label.isNotBlank())
            assertTrue("${variant.name} blurb", variant.blurb.isNotBlank())
        }
    }
}
