package com.ivandsky.puzzlebox.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecapPaletteContrastTest {

    private val themes = listOf(false, true)

    // Catches an ink retuned too light: body text on a card field would stop being readable.
    @Test
    fun `every ink holds seven to one on its field in both themes`() {
        forEachCard { card, dark, colors ->
            val ratio = contrast(colors.ink, colors.field)
            assertTrue("$card dark=$dark ink on field $ratio", ratio >= 7f)
        }
    }

    // Catches a plate lifted so far that a band's text on it drops under the text floor.
    @Test
    fun `every ink holds four and a half to one on its plate in both themes`() {
        forEachCard { card, dark, colors ->
            val ratio = contrast(colors.ink, colors.plate)
            assertTrue("$card dark=$dark ink on plate $ratio", ratio >= 4.5f)
        }
    }

    // Catches a control inside a plate whose 1.5dp ink outline cannot be found without its label.
    @Test
    fun `the control outline holds three to one against the plate in both themes`() {
        forEachCard { card, dark, colors ->
            val ratio = contrast(colors.ink, colors.plate)
            assertTrue("$card dark=$dark outline on plate $ratio", ratio >= 3f)
        }
    }

    // Catches a swapped or mistyped token that makes two story neighbours the same colour.
    @Test
    fun `no two adjacent cards in the story order share a field`() {
        for (dark in themes) {
            RecapCard.entries.zipWithNext().forEach { (a, b) ->
                assertNotEquals(
                    "$a and $b share a field, dark=$dark",
                    recapCardColors(a, dark).field,
                    recapCardColors(b, dark).field,
                )
            }
        }
    }

    // Catches a lookup that ignores the theme flag: dark would show light colours.
    @Test
    fun `the lookup returns the light set for light and the dark set for dark`() {
        assertEquals(LightRecapCoverField, recapCardColors(RecapCard.Cover, false).field)
        assertEquals(DarkRecapCoverField, recapCardColors(RecapCard.Cover, true).field)
        assertEquals(LightOnShare, recapCardColors(RecapCard.Share, false).ink)
        assertEquals(DarkSharePlate, recapCardColors(RecapCard.Share, true).plate)
    }

    // Catches a card added to the enum without colours: its lookup would throw at draw time.
    @Test
    fun `all ten cards have colours in both themes`() {
        assertEquals(10, RecapCard.entries.size)
        assertEquals(RecapCard.entries.toSet(), LightRecapCards.keys)
        assertEquals(RecapCard.entries.toSet(), DarkRecapCards.keys)
    }

    private fun forEachCard(check: (RecapCard, Boolean, RecapCardColors) -> Unit) {
        for (dark in themes) for (card in RecapCard.entries) check(card, dark, recapCardColors(card, dark))
    }

    private fun contrast(foreground: Color, background: Color): Float {
        val top = foreground.luminance() + 0.05f
        val bottom = background.luminance() + 0.05f
        return if (top > bottom) top / bottom else bottom / top
    }
}
