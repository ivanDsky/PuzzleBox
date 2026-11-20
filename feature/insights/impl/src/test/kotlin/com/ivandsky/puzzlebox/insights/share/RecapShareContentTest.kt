package com.ivandsky.puzzlebox.insights.share

import com.ivandsky.puzzlebox.designsystem.RecapCard as PaletteCard
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
                RecapShareContent.Stat("17 days", "Longest streak", PaletteCard.Streak),
                RecapShareContent.Stat("36 of 42", "Solved without a hint", PaletteCard.Solved),
                RecapShareContent.Stat("14", "Moments earned", PaletteCard.Moments),
                RecapShareContent.Stat("3h 12m", "Time spent solving", PaletteCard.TotalTime),
            ),
            content.tiles,
        )
        assertEquals("Ada", content.displayName)
    }

    @Test
    fun `the tiles keep their order whatever order the cards come in`() {
        val shuffled = RecapShareContent.of("2026", null, everything.reversed())

        assertEquals(
            listOf("Longest streak", "Solved without a hint", "Moments earned", "Time spent solving"),
            shuffled.tiles.map { it.label },
        )
    }

    @Test
    fun `one puzzle reads in the singular and has no tiles, only its game bar`() {
        val content = RecapShareContent.of("November 2026", null, ShareContract.empty)

        assertEquals("puzzle solved", content.hero!!.label)
        assertTrue(content.tiles.isEmpty())
        assertEquals(1, content.split.size)
    }

    @Test
    fun `a one day streak reads in the singular`() {
        val content = RecapShareContent.of("2026", null, listOf(RecapFixtures.solved, RecapFixtures.streak.copy(days = 1)))

        assertEquals("1 day", content.tiles.first().value)
    }

    @Test
    fun `the time tile reads hours and minutes like the hero, never h-mm-ss`() {
        val content = RecapShareContent.of("2026", null, ShareContract.longest)

        assertEquals("99h 59m", content.tiles.last().value)
    }

    @Test
    fun `the game bar splits the solves per game in the solved card's order`() {
        val content = RecapShareContent.of("October 2026", "Ivan", ShareContract.default)

        assertEquals(listOf(28, 24, 22, 14, 8), content.split.map { it.solved })
    }

    @Test
    fun `a screen reader hears final values and no name when none is shown`() {
        val named = RecapShareContent.of("October 2026", "Ivan", ShareContract.default).description()
        val hidden = RecapShareContent.of("October 2026", null, ShareContract.default).description()

        assertEquals(
            "Share image: October 2026, Ivan. 96 puzzles solved. Longest streak 23 days. Solved without a hint 71 of 96. " +
                "Moments earned 6. Time spent solving 3h 47m.",
            named,
        )
        assertEquals("Share image: October 2026. 96 puzzles solved.", hidden.substringBefore(" Longest"))
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
        val content = RecapShareContent.of("2026", null, listOf(RecapCard.Solved(42, emptyList()), RecapCard.Moments(emptyList())))

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
