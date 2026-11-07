package com.ivandsky.puzzlebox.games.strandsui.board

import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.render.LinkKind
import com.ivandsky.puzzlebox.games.strands.render.StrandsLink
import org.junit.Assert.assertEquals
import org.junit.Test

// Spec A14: found theme words alternate pink and magenta in the order they were found;
// the spangram and the live trace never count towards the alternation.
class AltThemeLinksTest {

    @Test
    fun `every second found theme word takes the alt fill`() {
        val first = StrandsLink(listOf(Coord(0, 0), Coord(0, 1)), LinkKind.Theme)
        val spangram = StrandsLink(listOf(Coord(1, 0), Coord(1, 1)), LinkKind.Spangram)
        val second = StrandsLink(listOf(Coord(2, 0), Coord(2, 1)), LinkKind.Theme)
        val third = StrandsLink(listOf(Coord(3, 0), Coord(3, 1)), LinkKind.Theme)
        val trace = StrandsLink(listOf(Coord(3, 2)), LinkKind.Trace)

        val alt = altThemeLinks(listOf(first, spangram, second, third, trace))

        assertEquals(setOf(second), alt)
        assertEquals(setOf(Coord(2, 0), Coord(2, 1)), alt.altThemeCells())
    }
}
