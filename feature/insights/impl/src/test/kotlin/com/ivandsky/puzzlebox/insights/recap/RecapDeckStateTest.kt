package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.graphics.Color
import com.ivandsky.puzzlebox.designsystem.GameAccent
import com.ivandsky.puzzlebox.designsystem.RecapCard as PaletteEntry
import com.ivandsky.puzzlebox.designsystem.RecapCardColors
import com.ivandsky.puzzlebox.designsystem.monthPalette
import com.ivandsky.puzzlebox.designsystem.recapCardColors
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The deck's moves without a screen: where the story is after each step, jump, replay and late page. */
class RecapDeckStateTest {

    /** Solved, Fastest with three games, Total time, then the Share page: six pages. */
    private val cards = listOf(RecapCard.Solved(total = 9, perGame = emptyList()), fastestOf(3), RecapFixtures.totalTime)
    private val pages = pagesOf(cards, share = true)

    @Test
    fun `forward and back walk the pages and stop at both ends`() {
        val deck = RecapDeckState(cardPages(3))

        assertFalse("first card: nowhere to go back to", deck.back())
        assertTrue(deck.forward())
        assertTrue(deck.forward())
        assertFalse("last page: forward stays put", deck.forward())
        assertEquals(2, deck.page)
        assertTrue(deck.back())
        assertEquals(1, deck.page)
        assertEquals(DeckMove.Back, deck.move)
    }

    @Test
    fun `back from the first card is left to the screen so system back leaves the recap`() {
        val deck = RecapDeckState(cardPages(3))

        assertFalse(deck.canStepBack)
        deck.forward()
        assertTrue(deck.canStepBack)
    }

    @Test
    fun `jumpTo lands on the card's page, reports its direction and stays inside the pages`() {
        val deck = RecapDeckState(cardPages(4))

        deck.jumpTo(2)
        assertEquals(2, deck.page)
        assertEquals(DeckMove.Forward, deck.move)
        deck.jumpTo(0)
        assertEquals(DeckMove.Back, deck.move)
        deck.jumpTo(99)
        assertEquals(3, deck.page)
        deck.jumpTo(-5)
        assertEquals(0, deck.page)
    }

    @Test
    fun `a saved page past the last clamps to the last page`() {
        assertEquals(4, RecapDeckState(cardPages(5), initialPage = 40).page)
        assertEquals(1, RecapDeckState(cardPages(5), initialPage = 1).page)
    }

    @Test
    fun `a fresh open starts on the cover and the first step reveals card one`() {
        val deck = RecapDeckState(cardPages(3), coverFirst = true)

        assertTrue(deck.onCover)
        assertNull(deck.current)
        assertEquals(0, deck.filledSegments)
        assertEquals(RecapDeckState.NoFact, deck.selectedFact)
        assertFalse(deck.canStepBack)
        assertTrue(deck.forward())
        assertFalse(deck.onCover)
        assertEquals(0, deck.page)
        assertEquals(1, deck.filledSegments)
    }

    @Test
    fun `a restored story resumes on its saved card with the cover dismissed`() {
        val deck = RecapDeckState(cardPages(3), initialPage = 2, coverFirst = true)

        assertFalse(deck.onCover)
        assertEquals(2, deck.page)
    }

    @Test
    fun `replay returns to the cover and clears the selected fact`() {
        val deck = RecapDeckState(cardPages(3))
        deck.jumpTo(2)

        deck.replay()

        assertTrue(deck.onCover)
        assertEquals(0, deck.page)
        assertEquals(RecapDeckState.NoFact, deck.selectedFact)
        assertEquals(0, deck.filledSegments)
    }

    @Test
    fun `a jump off the cover dismisses it`() {
        val deck = RecapDeckState(cardPages(3), coverFirst = true)

        deck.jumpTo(1)

        assertFalse(deck.onCover)
        assertEquals(1, deck.selectedFact)
    }

    @Test
    fun `hold is a flag the moves leave alone`() {
        val deck = RecapDeckState(cardPages(3))

        deck.hold(true)
        deck.forward()

        assertTrue(deck.holding)
        deck.hold(false)
        assertFalse(deck.holding)
    }

    @Test
    fun `a fresh open deals once, and a restored story is not dealt again on the same pages`() {
        val fresh = RecapDeckState(pages, initialPage = 3)
        val saved = with(RecapDeckState.Saver) { SaverScope { true }.save(fresh) }!!
        val restored = RecapDeckState.Saver.restore(saved)!!

        assertTrue(fresh.dealing)
        assertEquals(3, restored.page)
        assertEquals(pages, restored.pages)
        assertEquals(RecapPage(1, 2), restored.current)
        assertFalse(restored.dealing)
        fresh.dealt()
        assertFalse(fresh.dealing)
    }

    @Test
    fun `replaying a card bumps its entrance and leaves the page where it is`() {
        val deck = RecapDeckState(cardPages(3), initialPage = 1)

        deck.restartEntrance()

        assertEquals(1, deck.entrance)
        assertEquals(1, deck.page)
    }

    @Test
    fun `a page arriving late grows the strip without moving the card on screen`() {
        val deck = RecapDeckState(cardPages(5), initialPage = 2)

        deck.resize(cardPages(6))

        assertEquals(6, deck.segmentCount)
        assertEquals(2, deck.page)
        deck.resize(cardPages(2))
        assertEquals(1, deck.page)
    }

    @Test
    fun `the strip has one segment per page and Fastest one per game`() {
        val deck = RecapDeckState(pages)

        assertEquals(1 + 3 + 1 + 1, deck.segmentCount)
        deck.jumpTo(1)
        deck.forward()
        assertEquals(3, deck.filledSegments)
    }

    @Test
    fun `every game page selects the Fastest fact, and the page after it the next card`() {
        val deck = RecapDeckState(pages)

        assertEquals(0, deck.selectedFact)
        for (game in 0 until 3) {
            deck.forward()
            assertEquals(RecapPage(1, game), deck.current)
            assertEquals(1, deck.selectedFact)
        }
        deck.forward()
        assertEquals(2, deck.selectedFact)
        deck.forward()
        assertEquals("the Share page is past the last card", 3, deck.selectedFact)
    }

    @Test
    fun `a jump to Fastest lands on its first game and a jump past it skips its games`() {
        val deck = RecapDeckState(pages, coverFirst = true)

        deck.jumpTo(2)
        assertEquals(4, deck.page)
        assertEquals(RecapPage(2), deck.current)
        deck.jumpTo(1)
        assertEquals(1, deck.page)
        assertEquals(RecapPage(1, 0), deck.current)
        assertEquals(DeckMove.Back, deck.move)
    }

    @Test
    fun `each card and the Share page paint with their own palette entry, each game its own accent edge to edge`() {
        val deck = RecapDeckState(pages, coverFirst = true)
        val palette = { pagePalette(cards, deck.current, dark = false, accent = ::accentOf) }
        val entry = { it: PaletteEntry -> RecapPagePalette(recapCardColors(it, darkTheme = false)) }

        assertEquals(entry(PaletteEntry.Cover), palette())
        deck.forward()
        assertEquals(entry(PaletteEntry.Solved), palette())
        for (game in 0 until 3) {
            deck.forward()
            val accent = accentOf((cards[1] as RecapCard.Fastest).rows[game].game)
            assertEquals(RecapPagePalette(RecapCardColors(accent.accent, accent.onAccent, accent.accent), flat = true), palette())
        }
        deck.forward()
        assertEquals(entry(PaletteEntry.TotalTime), palette())
        deck.forward()
        assertEquals(entry(PaletteEntry.Share), palette())
    }

    // Catches a month card painting the old shared month entry: each month must shift the story to its own color.
    @Test
    fun `each month card paints with the colors of its own month in either theme`() {
        val months = listOf(RecapFixtures.march, RecapFixtures.april)
        for (dark in listOf(false, true)) {
            months.forEachIndexed { index, card ->
                val palette = pagePalette(months, RecapPage(index), dark = dark, accent = ::accentOf)
                assertEquals(RecapPagePalette(monthPalette(card.month.month, dark)), palette)
            }
        }
    }

    /** A distinct accent per game, so each game page is told apart by its color. */
    private fun accentOf(game: GameId): GameAccent {
        val shade = game.value.hashCode().toLong() and 0xFFFFFF
        return GameAccent(Color(0xFF000000 or shade), Color.Black, Color.White, Color.Black)
    }
}

/** [count] cards, one page each and no Share page. */
internal fun cardPages(count: Int): List<RecapPage> = List(count) { RecapPage(it) }
