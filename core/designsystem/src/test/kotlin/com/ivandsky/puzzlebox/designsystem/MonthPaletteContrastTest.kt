package com.ivandsky.puzzlebox.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import java.time.Month
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthPaletteContrastTest {

    private val themes = listOf(false, true)

    // Catches a month ink retuned too light: the hero, headline and stamp captions would stop reading on the card.
    @Test
    fun `every month ink holds four and a half to one on its field in both themes`() {
        forEachMonth { month, dark, colors ->
            val ratio = contrast(colors.ink, colors.field)
            assertTrue("$month dark=$dark ink on field $ratio", ratio >= 4.5f)
        }
    }

    // Catches a plate lifted so far that the plate rows' labels and values drop under the text floor.
    @Test
    fun `every month ink holds four and a half to one on its plate in both themes`() {
        forEachMonth { month, dark, colors ->
            val ratio = contrast(colors.ink, colors.plate)
            assertTrue("$month dark=$dark ink on plate $ratio", ratio >= 4.5f)
        }
    }

    // Catches two months sharing a field: flicking from one month card to the next would show no color change.
    @Test
    fun `every month has its own field in each theme`() {
        for (dark in themes) {
            val fields = Month.entries.map { monthPalette(it, dark).field }
            assertEquals("dark=$dark", Month.entries.size, fields.toSet().size)
        }
    }

    // Catches the theme flag being ignored: a dark recap would paint the light month fields.
    @Test
    fun `the dark theme paints each month in its own dark colors`() {
        for (month in Month.entries) assertNotEquals("$month", monthPalette(month, false), monthPalette(month, true))
    }

    // Catches a month card falling back to the old shared grey-blue month field.
    @Test
    fun `no month paints the old grey month field`() {
        for (month in Month.entries) {
            assertNotEquals("$month light", LightRecapYearMonthField, monthPalette(month, false).field)
            assertNotEquals("$month dark", DarkRecapYearMonthField, monthPalette(month, true).field)
        }
    }

    private fun forEachMonth(check: (Month, Boolean, RecapCardColors) -> Unit) {
        for (dark in themes) for (month in Month.entries) check(month, dark, monthPalette(month, dark))
    }

    private fun contrast(foreground: Color, background: Color): Float {
        val top = foreground.luminance() + 0.05f
        val bottom = background.luminance() + 0.05f
        return if (top > bottom) top / bottom else bottom / top
    }
}
