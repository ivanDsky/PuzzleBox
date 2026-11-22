package com.ivandsky.puzzlebox.designsystem.community

import androidx.compose.ui.unit.dp
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

// Community shelf cards (#2027): the dims and press values come from the handoff of the
// shelves prototype (02-community-shelves, B1+B2), so a drift in either shows here first.
class ShelfCardDefaultsTest {

    @Test
    fun `a shelf card is 156 wide with a 120 tall art and 20dp corners`() {
        assertEquals(156.dp, ShelfCardDefaults.CardWidth)
        assertEquals(120.dp, ShelfCardDefaults.ArtHeight)
        assertEquals(20.dp, ShelfCardDefaults.CardCorner)
    }

    @Test
    fun `the spotlight art is 112 with a 64 disc`() {
        assertEquals(112.dp, ShelfCardDefaults.SpotlightArtSize)
        assertEquals(64.dp, ShelfCardDefaults.SpotlightDiscSize)
        assertEquals(56.dp, ShelfCardDefaults.DiscSize)
    }

    @Test
    fun `the make card is 132 in a shelf and 200 in the make row`() {
        assertEquals(132.dp, ShelfCardDefaults.MakeCardWidth)
        assertEquals(200.dp, ShelfCardDefaults.MakeRowCardWidth)
    }

    @Test
    fun `cards press to 0_97 and the spotlight to 0_985 over 120ms`() {
        assertEquals(0.97f, ShelfCardDefaults.PressScale, 0f)
        assertEquals(0.985f, ShelfCardDefaults.SpotlightPressScale, 0f)
        assertEquals(120, ShelfCardDefaults.PressMillis)
    }

    @Test
    fun `a pressed card scales and a rested or reduced one stays at full size`() {
        assertEquals(0.97f, ShelfCardDefaults.pressTarget(pressed = true, reduced = false, scale = 0.97f), 0f)
        assertEquals(1f, ShelfCardDefaults.pressTarget(pressed = false, reduced = false, scale = 0.97f), 0f)
        assertEquals(1f, ShelfCardDefaults.pressTarget(pressed = true, reduced = true, scale = 0.97f), 0f)
    }

    @Test
    fun `reduced motion collapses the press duration to nothing`() {
        assertEquals(0, ShelfCardDefaults.pressMillis(reduced = true))
        assertEquals(120, ShelfCardDefaults.pressMillis(reduced = false))
    }

    @Test
    fun `solves group their thousands in the given locale`() {
        assertEquals("132", ShelfCardDefaults.solvesText(132, Locale.US))
        assertEquals("1,284", ShelfCardDefaults.solvesText(1284, Locale.US))
    }

    @Test
    fun `the byline is the author and the difficulty when there is one`() {
        assertEquals("Mia · Hard", ShelfCardDefaults.byline("Mia", "Hard"))
        assertEquals("Mia", ShelfCardDefaults.byline("Mia", null))
    }

    @Test
    fun `the spotlight line reads game, difficulty, then the author`() {
        assertEquals("Zip · Hard · by Mia", ShelfCardDefaults.spotlightLine("Zip", "Hard", "Mia"))
        assertEquals("Zip · by Mia", ShelfCardDefaults.spotlightLine("Zip", null, "Mia"))
    }

    @Test
    fun `the make label names the game`() {
        assertEquals("Make a Zip puzzle", ShelfCardDefaults.makeLabel("Zip"))
    }
}
