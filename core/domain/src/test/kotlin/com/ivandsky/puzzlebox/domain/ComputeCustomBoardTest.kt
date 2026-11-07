package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A friend puzzle's board as the result draws it: the author is pinned and never ranked, the
 * top five rank 1–5 in arrival order, my row joins below the cut, and an unreadable board reads
 * signed out or unavailable by whether anyone is signed in.
 */
class ComputeCustomBoardTest {

    private val compute = ComputeCustomBoard()
    private val author = AuthorPin(uid = "uid-author", name = "Jonas", timeMs = 102_000L, hints = 0)

    private fun entry(uid: String, timeMs: Long) =
        LeaderboardEntry(uid = uid, displayName = uid, puzzleRef = "custom/queens/K7Q2MX", timeMs = timeMs, hints = 0, completedAtMs = timeMs)

    private fun players(count: Int) = List(count) { entry("uid-${it + 1}", 60_000L + it * 1_000L) }

    @Test
    fun `the author's entry is dropped from the ranks and pinned`() {
        val entries = listOf(entry("uid-1", 50_000L), entry(author.uid, 55_000L), entry("uid-2", 60_000L))

        val view = compute(entries, myUid = "uid-2", author = author, limit = 5) as CustomBoardView.Ranked

        assertEquals(author, view.author)
        assertEquals(listOf("uid-1", "uid-2"), view.top.map { it.entry.uid })
        assertEquals("the author's slot is not counted", listOf(1, 2), view.top.map { it.rank })
    }

    @Test
    fun `seven entries cut to five with my seventh-place row appended`() {
        val view = compute(players(7), myUid = "uid-7", author = author, limit = 5) as CustomBoardView.Ranked

        assertEquals(listOf(1, 2, 3, 4, 5), view.top.map { it.rank })
        assertTrue(view.top.none { it.isMe })
        assertEquals(RankedEntry(rank = 7, entry = players(7)[6], isMe = true), view.mine)
    }

    @Test
    fun `my row inside the cut is marked there and not repeated`() {
        val view = compute(players(7), myUid = "uid-3", author = author, limit = 5) as CustomBoardView.Ranked

        assertEquals(listOf(false, false, true, false, false), view.top.map { it.isMe })
        assertNull(view.mine)
    }

    @Test
    fun `signed out, nobody is marked as me`() {
        val view = compute(players(3), myUid = null, author = author, limit = 5) as CustomBoardView.Ranked

        assertTrue(view.top.none { it.isMe })
        assertNull(view.mine)
    }

    @Test
    fun `no board with nobody signed in reads SignedOut`() {
        assertEquals(CustomBoardView.SignedOut(author), compute(null, myUid = null, author = author, limit = 5))
    }

    @Test
    fun `no board while signed in reads Unavailable`() {
        assertEquals(CustomBoardView.Unavailable(author), compute(null, myUid = "uid-1", author = author, limit = 5))
    }
}
