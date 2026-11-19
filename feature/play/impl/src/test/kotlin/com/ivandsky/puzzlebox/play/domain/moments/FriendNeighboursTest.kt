package com.ivandsky.puzzlebox.play.domain.moments

import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.domain.ranks.RankedBoard
import com.ivandsky.puzzlebox.domain.ranks.rankBoard
import com.ivandsky.puzzlebox.game.RankOrder
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** L4: the friend just above the player, else the friend just below, else none. */
class FriendNeighboursTest {

    private val ref = LeaderboardRef("alpha", LocalDate.of(2026, 11, 16))

    private fun entry(uid: String, timeMs: Long) =
        LeaderboardEntry(uid, uid.replaceFirstChar(Char::uppercase), "alpha/${ref.day}/1", timeMs, 0, 1L)

    private fun board(vararg entries: LeaderboardEntry): RankedBoard =
        rankBoard(ref, entries.toList(), RankOrder.TimeThenHints, entries.size)

    private fun friend(uid: String) = FriendNeighbours.Friend(uid, uid.replaceFirstChar(Char::uppercase))

    @Test
    fun `the friend just above is the nearest, not the leader`() {
        val neighbours = board(entry("ann", 10), entry("bea", 20), entry("me", 30), entry("cy", 40), entry("dee", 50))
            .friendNeighbours("me")

        assertEquals(FriendNeighbours(above = friend("bea"), below = friend("cy")), neighbours)
        assertEquals(friend("bea"), neighbours.nearest)
    }

    @Test
    fun `with nobody above the friend just below is the nearest`() {
        val neighbours = board(entry("me", 10), entry("ann", 20), entry("bea", 30)).friendNeighbours("me")

        assertEquals(FriendNeighbours(above = null, below = friend("ann")), neighbours)
        assertEquals(friend("ann"), neighbours.nearest)
    }

    @Test
    fun `a friend on the same place is not beaten yet, so counts as above`() {
        val neighbours = board(entry("me", 20), entry("ann", 20), entry("bea", 30)).friendNeighbours("me")

        assertEquals(friend("ann"), neighbours.above)
    }

    @Test
    fun `a player alone on the board has no neighbour`() {
        assertNull(board(entry("me", 10)).friendNeighbours("me").nearest)
    }

    @Test
    fun `a player not on the board has no neighbour`() {
        assertEquals(FriendNeighbours(above = null, below = null), board(entry("ann", 10), entry("bea", 20)).friendNeighbours("me"))
    }
}
