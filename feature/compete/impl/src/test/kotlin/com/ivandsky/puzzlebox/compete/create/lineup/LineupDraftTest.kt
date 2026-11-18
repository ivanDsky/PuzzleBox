package com.ivandsky.puzzlebox.compete.create.lineup

import com.ivandsky.puzzlebox.compete.create.GAMES
import com.ivandsky.puzzlebox.compete.create.MINI
import com.ivandsky.puzzlebox.compete.create.QUEENS
import com.ivandsky.puzzlebox.compete.create.TANGO
import com.ivandsky.puzzlebox.compete.create.ZIP
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.domain.lineup.AnyGame
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The lineup's pure edits: add, replace, remove, the cap of ten and the one that always stays. */
class LineupDraftTest {

    private fun draftOf(games: List<GameId>) = LineupDraft(games.map(::AnyGame))

    @Test
    fun `it opens with three Any rows, one game after another, and empty until games are known`() {
        assertEquals(draftOf(listOf(ZIP, TANGO, QUEENS)), LineupDraft.opening(GAMES))
        assertEquals(draftOf(listOf(ZIP, MINI, ZIP)), LineupDraft.opening(GAMES.filter { it.id == ZIP || it.id == MINI }))
        assertTrue(LineupDraft.opening(emptyList()).entries.isEmpty())
    }

    @Test
    fun `add appends, replace swaps one row and remove closes the gap, keeping order`() {
        val draft = draftOf(listOf(ZIP, QUEENS, QUEENS)).added(AnyGame(TANGO))
        assertEquals(draftOf(listOf(ZIP, QUEENS, QUEENS, TANGO)), draft)

        assertEquals(draftOf(listOf(ZIP, TANGO, QUEENS, TANGO)), draft.replaced(1, AnyGame(TANGO)))
        assertEquals(draftOf(listOf(QUEENS, QUEENS, TANGO)), draft.removed(0))
    }

    @Test
    fun `a lineup holds at most ten puzzles`() {
        val full = LineupDraft(List(Match.MAX_LEGS) { AnyGame(ZIP) })

        assertFalse(full.canAdd)
        assertSame(full, full.added(AnyGame(TANGO)))
        assertTrue(full.removed(0).canAdd)
    }

    @Test
    fun `the last puzzle cannot be removed`() {
        val one = draftOf(listOf(ZIP))

        assertFalse(one.canRemove)
        assertSame(one, one.removed(0))
    }

    @Test
    fun `an edit at a row that does not exist changes nothing`() {
        val draft = draftOf(listOf(ZIP, TANGO))

        assertSame(draft, draft.replaced(2, AnyGame(QUEENS)))
        assertSame(draft, draft.removed(-1))
    }
}
