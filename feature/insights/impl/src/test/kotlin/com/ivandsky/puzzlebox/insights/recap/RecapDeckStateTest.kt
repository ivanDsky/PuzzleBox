package com.ivandsky.puzzlebox.insights.recap

import androidx.compose.runtime.saveable.SaverScope
import com.ivandsky.puzzlebox.designsystem.RecapCard as PaletteEntry
import com.ivandsky.puzzlebox.insights.domain.recap.RecapCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The deck's moves without a screen: where the story is after each step, jump, replay and late page. */
class RecapDeckStateTest {

    @Test
    fun `forward and back walk the pages and stop at both ends`() {
        val deck = RecapDeckState(pageCount = 3)

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
        val deck = RecapDeckState(pageCount = 3)

        assertFalse(deck.canStepBack)
        deck.forward()
        assertTrue(deck.canStepBack)
    }

    @Test
    fun `jumpTo lands on the page, reports its direction and stays inside the pages`() {
        val deck = RecapDeckState(pageCount = 4)

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
        assertEquals(4, RecapDeckState(pageCount = 5, initialPage = 40).page)
        assertEquals(1, RecapDeckState(pageCount = 5, initialPage = 1).page)
    }

    @Test
    fun `a fresh open starts on the cover and the first step reveals card one`() {
        val deck = RecapDeckState(pageCount = 3, coverFirst = true)

        assertTrue(deck.onCover)
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
        val deck = RecapDeckState(pageCount = 3, initialPage = 2, coverFirst = true)

        assertFalse(deck.onCover)
        assertEquals(2, deck.page)
    }

    @Test
    fun `replay returns to the cover and clears the selected fact`() {
        val deck = RecapDeckState(pageCount = 3)
        deck.jumpTo(2)

        deck.replay()

        assertTrue(deck.onCover)
        assertEquals(0, deck.page)
        assertEquals(RecapDeckState.NoFact, deck.selectedFact)
        assertEquals(0, deck.filledSegments)
    }

    @Test
    fun `a jump off the cover dismisses it`() {
        val deck = RecapDeckState(pageCount = 3, coverFirst = true)

        deck.jumpTo(1)

        assertFalse(deck.onCover)
        assertEquals(1, deck.selectedFact)
    }

    @Test
    fun `hold is a flag the moves leave alone`() {
        val deck = RecapDeckState(pageCount = 3)

        deck.hold(true)
        deck.forward()

        assertTrue(deck.holding)
        deck.hold(false)
        assertFalse(deck.holding)
    }

    @Test
    fun `a fresh open deals once, and a restored story is not dealt again`() {
        val fresh = RecapDeckState(pageCount = 3, initialPage = 1)
        val saved = with(RecapDeckState.Saver) { SaverScope { true }.save(fresh) }!!
        val restored = RecapDeckState.Saver.restore(saved)!!

        assertTrue(fresh.dealing)
        assertEquals(1, restored.page)
        assertFalse(restored.dealing)
        fresh.dealt()
        assertFalse(fresh.dealing)
    }

    @Test
    fun `replaying a card bumps its entrance and leaves the page where it is`() {
        val deck = RecapDeckState(pageCount = 3, initialPage = 1)

        deck.restartEntrance()

        assertEquals(1, deck.entrance)
        assertEquals(1, deck.page)
    }

    @Test
    fun `a page arriving late grows the strip without moving the card on screen`() {
        val deck = RecapDeckState(pageCount = 5, initialPage = 2)

        deck.resize(6)

        assertEquals(6, deck.segmentCount)
        assertEquals(2, deck.page)
        deck.resize(2)
        assertEquals(1, deck.page)
    }

    @Test
    fun `the strip has one segment per page and Fastest is one segment however many games it holds`() {
        val fastest = RecapCard.Fastest(
            listOf(fastestRow("queens"), fastestRow("zip"), fastestRow("tango")),
        )
        val cards = listOf(RecapCard.Solved(total = 9, perGame = emptyList()), fastest, RecapFixtures.totalTime)

        val deck = RecapDeckState(pageCount = cards.size + 1)

        assertEquals(4, deck.segmentCount)
    }

    @Test
    fun `each card and the Share page paint with their own palette entry`() {
        val cards = listOf(RecapCard.Solved(total = 1, perGame = emptyList()), RecapFixtures.totalTime)
        val deck = RecapDeckState(pageCount = 3, coverFirst = true)

        assertEquals(PaletteEntry.Cover, recapPalette(cards, deck))
        deck.forward()
        assertEquals(PaletteEntry.Solved, recapPalette(cards, deck))
        deck.forward()
        assertEquals(PaletteEntry.TotalTime, recapPalette(cards, deck))
        deck.forward()
        assertEquals(PaletteEntry.Share, recapPalette(cards, deck))
    }

    private fun fastestRow(game: String) = RecapCard.Fastest.Row(
        game = com.ivandsky.puzzlebox.model.GameId(game),
        timeMs = 1_000,
        day = java.time.LocalDate.of(2026, 11, 1),
        puzzleNumber = null,
        hasReplay = false,
        averageMs = null,
        solves = 1,
    )
}
