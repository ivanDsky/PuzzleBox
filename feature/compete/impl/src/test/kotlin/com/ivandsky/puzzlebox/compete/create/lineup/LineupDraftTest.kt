package com.ivandsky.puzzlebox.compete.create.lineup

import com.ivandsky.puzzlebox.compete.create.GAMES
import com.ivandsky.puzzlebox.compete.create.MINI
import com.ivandsky.puzzlebox.compete.create.QUEENS
import com.ivandsky.puzzlebox.compete.create.TANGO
import com.ivandsky.puzzlebox.compete.create.ZIP
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.domain.lineup.AnyGame
import com.ivandsky.puzzlebox.domain.lineup.ExactPuzzle
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
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
    fun `today is one exact row per unsolved numbered game, in order, and only those rows are today's`() {
        val day = LocalDate.of(2026, 11, 9)
        val games = listOf(
            GAMES[0].copy(todayNumber = 12, todayDay = day),
            GAMES[1].copy(todayNumber = 3, todayDay = day, solvedToday = true),
            GAMES[2].copy(todayNumber = null, todayDay = day),
            GAMES[3].copy(todayNumber = 9, todayDay = day),
        )

        val today = LineupDraft.today(games)

        assertEquals(
            listOf("Zip #12", "Mini Sudoku #9"),
            today.entries.map { (it as ExactPuzzle).title },
        )
        assertEquals(LegRef.Daily(ZIP, day, 12), (today.entries[0] as ExactPuzzle).ref)
        assertTrue(today.isToday(games))
        assertFalse(today.added(AnyGame(ZIP)).isToday(games))
        assertFalse(LineupDraft(emptyList()).isToday(games))
        val yesterday = LineupDraft(listOf((today.entries[0] as ExactPuzzle).let { it.copy(ref = LegRef.Daily(ZIP, day.minusDays(1), 11)) }))
        assertFalse(yesterday.isToday(games))
        assertTrue(LineupDraft.today(GAMES).entries.isEmpty())
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

    @Test
    fun `moving a puzzle to another slot keeps the others in order, at either end of the list`() {
        val draft = draftOf(listOf(ZIP, TANGO, QUEENS, MINI))

        assertEquals(draftOf(listOf(QUEENS, ZIP, TANGO, MINI)), draft.moved(2, 0))
        assertEquals(draftOf(listOf(TANGO, QUEENS, MINI, ZIP)), draft.moved(0, 3))
        assertEquals(draftOf(listOf(ZIP, QUEENS, TANGO, MINI)), draft.moved(1, 2))
    }

    @Test
    fun `a move to the same slot or from or to a row that does not exist changes nothing`() {
        val draft = draftOf(listOf(ZIP, TANGO))

        assertSame(draft, draft.moved(1, 1))
        assertSame(draft, draft.moved(0, 2))
        assertSame(draft, draft.moved(-1, 0))
    }

    @Test
    fun `Past you opens with up to three Any rows in turn, only of games with a solved puzzle and never more of a game than solved`() {
        assertEquals(draftOf(listOf(ZIP, QUEENS, ZIP)), LineupDraft.openingSolved(GAMES, mapOf(ZIP to 2, TANGO to 0, QUEENS to 1)))
        assertEquals(draftOf(listOf(ZIP, TANGO, QUEENS)), LineupDraft.openingSolved(GAMES, mapOf(ZIP to 9, TANGO to 9, QUEENS to 9, MINI to 9)))
        assertEquals(draftOf(listOf(MINI, MINI)), LineupDraft.openingSolved(GAMES, mapOf(MINI to 2)))
        assertTrue(LineupDraft.openingSolved(GAMES, mapOf(ZIP to 0)).entries.isEmpty())
        assertTrue(LineupDraft.openingSolved(emptyList(), mapOf(ZIP to 3)).entries.isEmpty())
    }
}
