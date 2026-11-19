package com.ivandsky.puzzlebox.domain.ranks

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.leaderboard.BoardPage
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.linkedin.LinkedInGhost
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInBoardView
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInDayStatus
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInDayView
import com.ivandsky.puzzlebox.game.RankOrder
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * #1743: the viewer's own LinkedIn result counts on a Friends board the way a connection's does (ADR-0031 §11).
 * #1746: on a window a friend's LinkedIn-only days add to their one row.
 */
class ConnectionBoardsTest {

    private val ann = Identity("u1", "Ann", null)
    private val board = LeaderboardRef("tango", LocalDate.of(2026, 11, 14))

    private fun entry(uid: String, name: String, timeMs: Long) = LeaderboardEntry(uid, name, "tango/2026-11-14/301", timeMs, 0, 1_700_000_000_000)

    private val bea = entry("bea", "Bea", 60_000)
    private val zed = entry("zed", "Zed", 40_000)

    private fun day(imported: Long?, vararg connections: LinkedInGhost) = LinkedInDayView(
        imported?.let { LinkedInDayStatus.Imported(it) } ?: LinkedInDayStatus.Idle,
        LinkedInBoardView.Shown(connections.toList()),
    )

    private fun BoardPage.scores() = entries.map { Triple(it.uid, it.displayName, it.timeMs) } to count

    @Test
    fun `a day the viewer played only on LinkedIn counts them at their LinkedIn time, under their uid and PuzzleBox name`() {
        val friends = BoardPage(listOf(zed, bea), 2).friendBoard(setOf("u1", "bea"), day(imported = 50_000), ann)

        assertEquals(listOf(Triple("bea", "Bea", 60_000L), Triple("u1", "Ann", 50_000L)) to 2, friends.scores())
    }

    @Test
    fun `a connection named like the viewer is someone else, so the viewer is counted once and the connection at its own time`() {
        val namesake = LinkedInGhost("ref-other-ann", "Ann", 55_000, hintFree = true, flawless = false)

        val friends = BoardPage(listOf(bea), 1).friendBoard(setOf("u1", "bea"), day(imported = 50_000, namesake), ann)

        assertEquals(
            listOf(Triple("bea", "Bea", 60_000L), Triple("u1", "Ann", 50_000L), Triple(connectionUid("ref-other-ann"), "Ann", 55_000L)) to 3,
            friends.scores(),
        )
    }

    @Test
    fun `a viewer already on the board takes their LinkedIn time there and is not added again`() {
        val friends = BoardPage(listOf(bea, entry("u1", "Ann", 70_000)), 2).friendBoard(setOf("u1", "bea"), day(imported = 50_000), ann)

        assertEquals(listOf(Triple("bea", "Bea", 60_000L), Triple("u1", "Ann", 50_000L)) to 2, friends.scores())
    }

    @Test
    fun `no LinkedIn result of their own, or no viewer, adds nobody`() {
        val page = BoardPage(listOf(bea), 1)

        assertEquals(listOf(Triple("bea", "Bea", 60_000L)) to 1, page.friendBoard(setOf("u1", "bea"), day(imported = null), ann).scores())
        assertEquals(listOf(Triple("bea", "Bea", 60_000L)) to 1, page.friendBoard(setOf("u1", "bea"), day(imported = 50_000), viewer = null).scores())
    }

    @Test
    fun `the entry points place the viewer on their LinkedIn-only day as the hub's Friends board does`() {
        val standing = FriendDay(board, setOf("bea"), day(imported = 50_000)).standing(listOf(zed, bea), ann, RankOrder.TimeThenHints)

        assertEquals(FriendDay.Standing(place = 1, count = 2), standing)
    }

    private val nextDay = LeaderboardRef("tango", LocalDate.of(2026, 11, 15))
    private val players = setOf("u1", "bea", "cy")

    private fun loaded(ref: LeaderboardRef, page: BoardPage) = BoardLoad.Loaded(ref, RankOrder.TimeThenHints, page, final = false)

    private fun linkedIn(memberRef: String, name: String, timeMs: Long) = LinkedInGhost(memberRef, name, timeMs, hintFree = true, flawless = false)

    /** The first day holds [first] as the PuzzleBox board has them, the next one only [onLinkedIn], each as the Friends board counts them. */
    private fun window(first: List<LeaderboardEntry>, vararg onLinkedIn: LinkedInGhost) = listOf(
        loaded(board, BoardPage(first, first.size).friendBoard(players, connections = null, ann)),
        loaded(nextDay, BoardPage(emptyList(), 0).friendBoard(players, day(imported = null, *onLinkedIn), ann)),
    )

    @Test
    fun `a friend with a PuzzleBox day and a LinkedIn-only day is one row that adds both`() {
        val rows = windowStandings(window(listOf(zed, bea), linkedIn("ref-bea", "Bea", 50_000)), ann, emptyList()).rows

        assertEquals(listOf("bea"), rows.map { it.uid })
        assertEquals(2, rows.single().games)
        assertEquals(2 * percentilePoints(1, 1), rows.single().total, 1e-9)
        assertEquals("Bea", rows.single().displayName)
    }

    @Test
    fun `a connection named like the viewer is someone else on a window, as on a day`() {
        val rows = windowStandings(window(listOf(entry("u1", "Ann", 70_000)), linkedIn("ref-ann", "Ann", 50_000)), ann, emptyList()).rows

        assertEquals(setOf("u1", connectionUid("ref-ann")), rows.map { it.uid }.toSet())
    }

    @Test
    fun `a connection named like two friends is not known to be either, so it stays its own row`() {
        val twins = listOf(bea, entry("cy", "bea", 65_000))

        val rows = windowStandings(window(twins, linkedIn("ref-bea", "Bea", 50_000)), ann, emptyList()).rows

        assertEquals(setOf("bea", "cy", connectionUid("ref-bea")), rows.map { it.uid }.toSet())
    }
}
