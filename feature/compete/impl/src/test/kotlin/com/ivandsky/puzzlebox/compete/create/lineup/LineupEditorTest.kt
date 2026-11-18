package com.ivandsky.puzzlebox.compete.create.lineup

import com.ivandsky.puzzlebox.compete.create.GAMES
import com.ivandsky.puzzlebox.compete.create.QUEENS
import com.ivandsky.puzzlebox.compete.create.TANGO
import com.ivandsky.puzzlebox.compete.create.ZIP
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.domain.lineup.AnyGame
import com.ivandsky.puzzlebox.domain.lineup.ExactPuzzle
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The editor's sheet target, flash, refusal mark and the pinned puzzles of a failed Create. */
class LineupEditorTest {

    private val opening = LineupEditor.opening(GAMES)

    @Test
    fun `adding opens the sheet on the next row and a pick adds it, flashes it and closes the sheet`() {
        val picked = opening.openAdd().also { assertEquals(3, it.target) }.pick(AnyGame(TANGO))

        assertEquals(4, picked.draft.size)
        assertEquals(AnyGame(TANGO), picked.draft.entries.last())
        assertEquals(3, picked.flashed)
        assertNull(picked.target)
    }

    @Test
    fun `changing a row opens the sheet on it and a pick replaces it and flashes it`() {
        val sheet = opening.openChange(1)
        assertTrue(sheet.changing)

        val picked = sheet.pick(AnyGame(QUEENS))

        assertEquals(listOf(ZIP, QUEENS, QUEENS), picked.draft.entries.map { it.gameId })
        assertEquals(1, picked.flashed)
        assertEquals(null, picked.flashDone().flashed)
    }

    @Test
    fun `a full lineup has no Add row to open`() {
        val full = LineupEditor(LineupDraft(List(Match.MAX_LEGS) { AnyGame(ZIP) }))

        assertSame(full, full.openAdd())
    }

    @Test
    fun `a pick with no sheet open changes nothing`() {
        assertSame(opening, opening.pick(AnyGame(ZIP)))
    }

    @Test
    fun `a refused row is marked until the next edit`() {
        val refused = opening.refused(2)
        assertEquals(1, refused.marked)

        assertNull(refused.remove(0).marked)
        assertNull(refused.openChange(1).pick(AnyGame(ZIP)).marked)
    }

    @Test
    fun `pinned legs are what Create sends, as exact entries, until an edit drops them`() {
        val daily = MatchLeg(LegRef.Daily(ZIP, LocalDate.of(2026, 11, 1), 12), authorUid = null)
        val pinned = opening.pin(listOf(daily))

        val sent = pinned.entries.single() as ExactPuzzle
        assertEquals(daily.ref, sent.ref)
        assertEquals(null, sent.authorUid)
        assertEquals(opening.draft, pinned.draft)

        assertEquals(opening.entries, pinned.unpinned().entries)
        assertEquals(opening.remove(0).entries, pinned.remove(0).entries)
    }
}
