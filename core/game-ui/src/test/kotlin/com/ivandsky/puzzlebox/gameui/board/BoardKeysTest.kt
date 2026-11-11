package com.ivandsky.puzzlebox.gameui.board

import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a row is, for the board that moves rows about: the player or connection, never the place. */
class BoardKeysTest {
    private fun real(rank: Int, player: String, seconds: Int, isSelf: Boolean = false) =
        DailyBoardRowModel(rank, player, seconds * 1000L, 0, isSelf, playerId = player)

    private fun ghost(ref: String, seconds: Int?) = GhostRowModel(ref, "Name of $ref", seconds?.let { it * 1000L }, hintFree = true)

    private fun keysOf(lines: List<DailyBoardLine>, day: String? = "2026-11-10") = boardKeys(lines, day)

    private fun keyOfPlayer(lines: List<DailyBoardLine>, player: String, day: String? = "2026-11-10") =
        keysOf(lines, day)[lines.indexOfFirst { it is DailyBoardLine.Real && it.row.playerId == player }]

    @Test
    fun `the same player keeps the key when the place changes`() {
        val before = rankWithGhosts(listOf(real(1, "ada", 30), real(2, "bo", 40), real(3, "cy", 50)), emptyList(), RankOrder.TimeThenHints)
        val after = rankWithGhosts(listOf(real(1, "cy", 20), real(2, "ada", 30), real(3, "bo", 40)), emptyList(), RankOrder.TimeThenHints)

        for (player in listOf("ada", "bo", "cy")) {
            assertEquals(keyOfPlayer(before, player), keyOfPlayer(after, player))
        }
    }

    @Test
    fun `a connection keeps its key when a faster time takes it up a place`() {
        val rows = listOf(real(1, "ada", 30), real(2, "bo", 50))
        val slow = rankWithGhosts(rows, listOf(ghost("ref-x", 60)), RankOrder.TimeThenHints)
        val fast = rankWithGhosts(rows, listOf(ghost("ref-x", 40)), RankOrder.TimeThenHints)

        assertEquals(keysOf(slow).last(), keysOf(fast)[1])
    }

    @Test
    fun `a player and a connection never share a key`() {
        val lines = rankWithGhosts(listOf(real(1, "same", 30)), listOf(ghost("same", 40)), RankOrder.TimeThenHints)

        assertEquals(2, keysOf(lines).toSet().size)
    }

    @Test
    fun `another day's rows do not reuse the key of the same player`() {
        val lines = rankWithGhosts(listOf(real(1, "ada", 30), real(2, "bo", 40)), listOf(ghost("ref-x", 35)), RankOrder.TimeThenHints)

        val today = keysOf(lines, day = "2026-11-10")
        val yesterday = keysOf(lines, day = "2026-11-09")

        assertTrue(today.intersect(yesterday.toSet()).isEmpty())
    }

    @Test
    fun `the own row added from LinkedIn has one fixed key from sync to sync`() {
        val rows = listOf(real(1, "ada", 30), real(2, "bo", 50))
        val you = { seconds: Int -> LinkedInYou(BestScore(seconds * 1000L, 0), "Me", null) }
        val first = rankWithLinkedIn(rows, emptyList(), you(40), RankOrder.TimeThenHints)
        val second = rankWithLinkedIn(rows, emptyList(), you(20), RankOrder.TimeThenHints)

        fun added(lines: List<DailyBoardLine>) = keysOf(lines)[lines.indexOfFirst { it is DailyBoardLine.Real && it.row.fromLinkedIn }]
        assertEquals(added(first), added(second))
        assertNotEquals(keyOfPlayer(first, "ada"), added(first))
    }

    @Test
    fun `a list that names a player twice still gets distinct keys`() {
        val lines = listOf(DailyBoardLine.Real(real(1, "ada", 30), 1), DailyBoardLine.Real(real(2, "ada", 40), 2))

        assertEquals(2, keysOf(lines).toSet().size)
    }
}
