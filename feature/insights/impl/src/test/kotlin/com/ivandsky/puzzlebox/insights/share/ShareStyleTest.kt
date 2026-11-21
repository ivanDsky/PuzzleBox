package com.ivandsky.puzzlebox.insights.share

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.ivandsky.puzzlebox.designsystem.RecapCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The poster's text holds contrast on every style, light and dark, and the fixed styles never follow the viewer's theme. */
class ShareStyleTest {

    @Test
    fun `title, name, hero and wordmark hold 4_5 to 1 on the ground in every style and theme`() {
        for (style in ShareStyle.entries) for (dark in listOf(false, true)) {
            val p = sharePalette(style, dark)
            for ((what, color) in listOf("ink" to p.ink, "hero" to p.hero, "wordmark" to p.wordmark)) {
                assertTrue("$style dark=$dark $what ${contrast(color, p.ground)}", contrast(color, p.ground) >= 4.5)
            }
        }
    }

    @Test
    fun `every tile's ink holds 4_5 to 1 on its field in every style and theme`() {
        for (style in ShareStyle.entries) for (dark in listOf(false, true)) {
            val p = sharePalette(style, dark)
            for (card in listOf(RecapCard.Streak, RecapCard.Solved, RecapCard.Moments, RecapCard.TotalTime)) {
                val c = p.tile(card)
                assertTrue("$style dark=$dark $card", contrast(c.ink, c.field) >= 4.5)
            }
        }
    }

    @Test
    fun `Color and Night look the same in the dark theme, Classic does not`() {
        assertEquals(sharePalette(ShareStyle.Color, false), sharePalette(ShareStyle.Color, true))
        assertEquals(sharePalette(ShareStyle.Night, false), sharePalette(ShareStyle.Night, true))
        assertTrue(sharePalette(ShareStyle.Classic, false) != sharePalette(ShareStyle.Classic, true))
    }

    @Test
    fun `only Night has a holographic layer`() {
        assertTrue(sharePalette(ShareStyle.Night, false).holo.isNotEmpty())
        assertTrue(sharePalette(ShareStyle.Classic, false).holo.isEmpty())
        assertTrue(sharePalette(ShareStyle.Color, false).holo.isEmpty())
    }

    @Test
    fun `the games' accents are Night's dark ones and Color's light ones whatever the theme, and Classic follows it`() {
        for (dark in listOf(false, true)) {
            assertTrue(sharePalette(ShareStyle.Night, dark).accentsDark)
            assertFalse(sharePalette(ShareStyle.Color, dark).accentsDark)
            assertEquals(dark, sharePalette(ShareStyle.Classic, dark).accentsDark)
        }
    }

    @Test
    fun `the track behind the game tiles' rings is opaque in every style and theme`() {
        for (style in ShareStyle.entries) for (dark in listOf(false, true)) {
            assertEquals("$style dark=$dark", 1f, sharePalette(style, dark).trackSolid.alpha, 0f)
        }
    }

    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }
}
