package com.ivandsky.puzzlebox.history.ranks

import com.ivandsky.puzzlebox.domain.ranks.OverallStandings
import com.ivandsky.puzzlebox.domain.ranks.OverallState
import com.ivandsky.puzzlebox.gameui.board.BoardRowValue
import com.ivandsky.puzzlebox.gameui.board.DailyBoardState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every [OverallState] as the board kit draws it: rows, the viewer's own row, the note, 100+ and ≥ (A11, A12). */
class OverallBoardMappingTest {

    private fun row(uid: String, place: Int, total: Double, games: Int = 3, truncated: Boolean = false) =
        OverallStandings.Row(uid, uid.replaceFirstChar(Char::uppercase), place, total, games, truncated)

    private val standings = OverallStandings(
        listOf(row("maya", 1, 290.0), row("u1", 2, 250.5), row("jonas", 3, 120.0, games = 1)),
    )

    private fun ranked(state: OverallState, selfUid: String? = "u1") =
        state.toDailyBoardState(selfUid) as DailyBoardState.Ranked

    @Test
    fun `Loading and SignedOut pass through`() {
        assertEquals(DailyBoardState.Loading, OverallState.Loading.toDailyBoardState("u1"))
        assertEquals(DailyBoardState.SignedOut, OverallState.SignedOut.toDailyBoardState("u1"))
    }

    @Test
    fun `Empty is a ranked board with no rows`() {
        assertEquals(DailyBoardState.Ranked(emptyList()), OverallState.Empty.toDailyBoardState("u1"))
    }

    @Test
    fun `Complete puts every standing on the board as points rows with the viewer marked`() {
        val board = ranked(OverallState.Complete(standings))

        assertEquals(listOf(1, 2, 3), board.rows.map { it.rank })
        assertEquals(listOf("Maya", "U1", "Jonas"), board.rows.map { it.name })
        assertEquals(listOf(false, true, false), board.rows.map { it.isSelf })
        assertEquals(listOf("maya", "u1", "jonas"), board.rows.map { it.playerId })
        assertEquals(BoardRowValue.Points(250.5, 3, atLeast = false), board.rows[1].scoreValue)
        assertEquals(BoardRowValue.Points(120.0, 1, atLeast = false), board.rows[2].scoreValue)
        assertNull(board.pinned)
        assertNull(board.note)
        assertNull(OverallState.Complete(standings).boardNote())
    }

    @Test
    fun `nobody is the viewer when signed identity is unknown`() {
        assertTrue(ranked(OverallState.Complete(standings), selfUid = null).rows.none { it.isSelf })
    }

    @Test
    fun `a truncated row is a lower bound`() {
        val board = ranked(OverallState.Complete(OverallStandings(listOf(row("u1", 1, 233.3, truncated = true)))))

        assertEquals(BoardRowValue.Points(233.3, 3, atLeast = true), board.rows.single().scoreValue)
    }

    @Test
    fun `places past the page cap read 100+`() {
        val rows = listOf(row("a", 100, 10.0), row("u1", 101, 9.0))
        val board = ranked(OverallState.Complete(OverallStandings(rows)))

        assertEquals(listOf(false, true), board.rows.map { it.beyondPage })
        assertEquals(101, board.rows.last().rank)
    }

    @Test
    fun `Partial shows the standings so far and says how many boards are in`() {
        val state = OverallState.Partial(standings, loaded = 2, total = 5)

        assertEquals(3, ranked(state).rows.size)
        assertEquals("2 of 5 boards", state.boardNote())
    }

    @Test
    fun `Offline with saved boards shows them and says so`() {
        val state = OverallState.Offline(standings)

        assertEquals(3, ranked(state).rows.size)
        assertEquals("Offline — showing boards saved on this phone", state.boardNote())
    }

    @Test
    fun `Offline with nothing saved is unavailable, never a spinner`() {
        assertEquals(DailyBoardState.Unavailable, OverallState.Offline(null).toDailyBoardState("u1"))
        assertNull(OverallState.Offline(null).boardNote())
    }

    @Test
    fun `states without a note have none`() {
        assertFalse(listOf(OverallState.Loading, OverallState.SignedOut, OverallState.Empty).any { it.boardNote() != null })
    }
}
