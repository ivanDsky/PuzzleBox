package com.ivandsky.puzzlebox.domain.ranks

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.leaderboard.BoardPage
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import java.time.LocalDate
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** F18: Friends scope ranks the viewer and their friends among themselves, on every board and in Overall. */
class FriendBoardsTest {

    private val day = LocalDate.of(2026, 11, 13)
    private val order = RankOrder.TimeThenHints
    private val zip = LeaderboardRef("zip", day)
    private val tango = LeaderboardRef("tango", day)
    private val friends = setOf("ann", "bea", "cy")

    private fun entry(uid: String, board: LeaderboardRef, time: Long) =
        LeaderboardEntry(uid, uid.replaceFirstChar(Char::uppercase), "${board.gameId}/${board.day}/1", time, 0, 1L)

    private fun loaded(board: LeaderboardRef, vararg entries: LeaderboardEntry, count: Int = entries.size, final: Boolean = false) =
        BoardLoad.Loaded(board, order, BoardPage(entries.toList(), count), final)

    private fun ranked(boards: RankWindowBoards): List<RankedBoard> = (boards as RankWindowBoards.Boards).boards
        .filterIsInstance<BoardLoad.Loaded>()
        .map { rankBoard(it.board, it.page.entries, it.order, it.page.count) }

    private fun RankedBoard.places() = rows.map { Triple(it.entry.uid, it.rank, it.points) }

    private suspend fun overallOf(boards: RankWindowBoards): List<OverallStandings.Row> {
        val observe = ObserveOverall(FakeIdentityService(initial = Identity("ann", "Ann", null)), FakeCompletionRepository())
        return (observe(flowOf(boards)).drop(1).first() as OverallState.Complete).standings.rows
    }

    // Zip: strangers first and third. Tango: a stranger between the two friends who played.
    private val window = RankWindowBoards.Boards(
        listOf(
            loaded(zip, entry("sam", zip, 10), entry("bea", zip, 20), entry("tom", zip, 30), entry("ann", zip, 40), entry("cy", zip, 50)),
            loaded(tango, entry("ann", tango, 15), entry("sam", tango, 20), entry("cy", tango, 25)),
        ),
    )

    @Test
    fun `a friends board renumbers 1 to n, ties sharing a place, with N the friends who played`() {
        val board = RankWindowBoards.Boards(
            listOf(
                loaded(zip, entry("sam", zip, 10), entry("bea", zip, 20), entry("tom", zip, 30), entry("cy", zip, 20), entry("ann", zip, 40), count = 340),
            ),
        )

        val among = ranked(board.amongFriends(friends)).single()

        assertEquals(3, among.n)
        assertEquals(false, among.truncated)
        assertEquals(listOf("bea" to 1, "cy" to 1, "ann" to 3), among.rows.map { it.entry.uid to it.rank })
        assertEquals(listOf(100.0, 100.0, 100.0 / 3), among.rows.map { it.points })
    }

    @Test
    fun `Overall in Friends sums friend points over friend N, two games and three friends`() = runTest {
        val rows = overallOf(window.amongFriends(friends))

        // Zip among friends: Bea 1, Ann 2, Cy 3 of 3 → 100, 66.67, 33.33. Tango: Ann 1, Cy 2 of 2 → 100, 50.
        assertEquals(listOf("ann", "bea", "cy"), rows.map { it.uid })
        assertEquals(listOf(1, 2, 3), rows.map { it.place })
        assertEquals(listOf(2, 1, 2), rows.map { it.games })
        assertEquals(200.0 / 3 + 100.0, rows[0].total, TOLERANCE)
        assertEquals(100.0, rows[1].total, TOLERANCE)
        assertEquals(100.0 / 3 + 50.0, rows[2].total, TOLERANCE)
    }

    @Test
    fun `Global is the boards as read, places and points over everyone`() = runTest {
        assertEquals(
            listOf(Triple("sam", 1, 100.0), Triple("bea", 2, 80.0), Triple("tom", 3, 60.0), Triple("ann", 4, 40.0), Triple("cy", 5, 20.0)),
            ranked(window).first().places(),
        )
        val rows = overallOf(window)

        // Zip of 5: Sam 100, Bea 80, Tom 60, Ann 40, Cy 20. Tango of 3: Ann 100, Sam 66.67, Cy 33.33.
        assertEquals(listOf("sam" to 1, "ann" to 2, "bea" to 3, "tom" to 4, "cy" to 5), rows.map { it.uid to it.place })
        assertEquals(140.0, rows[1].total, TOLERANCE)
    }

    @Test
    fun `a board that is not loaded, and signed out, stay as they are`() {
        val boards = RankWindowBoards.Boards(
            listOf(BoardLoad.Loading(zip, order), BoardLoad.Unavailable(tango, order), loaded(zip, entry("sam", zip, 10), final = true)),
        )

        val among = (boards.amongFriends(friends) as RankWindowBoards.Boards).boards

        assertEquals(boards.boards.take(2), among.take(2))
        assertEquals(loaded(zip, final = true), among[2])
        assertEquals(RankWindowBoards.SignedOut, RankWindowBoards.SignedOut.amongFriends(friends))
    }

    private companion object {
        const val TOLERANCE = 1e-9
    }
}
