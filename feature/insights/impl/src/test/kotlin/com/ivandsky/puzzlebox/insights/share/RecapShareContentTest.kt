package com.ivandsky.puzzlebox.insights.share

import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.insights.recap.RecapFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the image and the text say: the player's own headline numbers and name (A9), and nothing a card has no number for. */
class RecapShareContentTest {

    private val everything = listOf(
        RecapFixtures.solved,
        RecapFixtures.fastest,
        RecapFixtures.streak,
        RecapFixtures.moments,
        RecapFixtures.totalTime,
    )

    @Test
    fun `the solved total is the hero and the other cards are tiles`() {
        val content = RecapShareContent.of("November 2026", "Ada", everything)

        assertEquals(RecapShareContent.Stat("42", "puzzles solved"), content.hero)
        assertEquals(
            listOf(
                RecapShareContent.Stat("17 days", "Longest streak"),
                RecapShareContent.Stat("36 of 42", "Solved without a hint"),
                RecapShareContent.Stat("23", "Moments earned"),
                RecapShareContent.Stat("3:12:15", "Time spent solving"),
            ),
            content.tiles,
        )
        assertEquals("Ada", content.displayName)
    }

    @Test
    fun `one puzzle and one day read in the singular`() {
        val content = RecapShareContent.of(
            "November 2026", null,
            listOf(RecapCard.Solved(1, emptyList()), RecapFixtures.streak.copy(days = 1)),
        )

        assertEquals("puzzle solved", content.hero!!.label)
        assertEquals("1 day", content.tiles.single().value)
    }

    @Test
    fun `a story with no hint-free solve has no hint-free tile, and with no streak the tile leads`() {
        val none = RecapShareContent.of("2026", null, listOf(RecapFixtures.solved.copy(hintFree = 0), RecapFixtures.streak))
        val noStreak = RecapShareContent.of("2026", null, listOf(RecapFixtures.solved, RecapFixtures.totalTime))

        assertEquals(listOf("Longest streak"), none.tiles.map { it.label })
        assertEquals(listOf("Solved without a hint", "Time spent solving"), noStreak.tiles.map { it.label })
    }

    @Test
    fun `a card with nothing to count adds no tile`() {
        val content = RecapShareContent.of("2026", null, listOf(RecapCard.Solved(42, emptyList()), RecapCard.Moments(0, 0, 0, emptyList())))

        assertTrue(content.tiles.isEmpty())
    }

    @Test
    fun `no solved card means no hero, and a blank or missing name is left out`() {
        assertNull(RecapShareContent.of("2026", "  ", listOf(RecapFixtures.streak)).hero)
        assertNull(RecapShareContent.of("2026", "  ", listOf(RecapFixtures.streak)).displayName)
        assertNull(RecapShareContent.of("2026", null, emptyList()).displayName)
    }

    @Test
    fun `the text carries the title, the number and the link and no game or friend detail`() {
        val text = RecapShareContent.of("2026 so far", "Ada", everything).shareText()

        assertEquals("My PuzzleBox 2026 so far: 42 puzzles solved. Get PuzzleBox: $RecapShareLink", text)
        assertFalse(text.contains("Ada"))
    }
}
