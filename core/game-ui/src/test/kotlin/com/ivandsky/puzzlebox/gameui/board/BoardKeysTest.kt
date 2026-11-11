package com.ivandsky.puzzlebox.gameui.board

import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.RankOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** What a row is, for the board that moves rows about: the player or connection, never the place. */
class BoardKeysTest {
    private fun real(rank: Int, player: String, seconds: Int, isSelf: Boolean = false) =
        DailyBoardRowModel(rank, player, seconds * 1000L, 0, isSelf, playerId = player)

    private fun ghost(ref: String, seconds: Int?) = GhostRowModel(ref, "Name of $ref", seconds?.let { it * 1000L }, hintFree = true)

    private fun keysOf(lines: List<DailyBoardLine>) = boardKeys(lines)

    private fun keyOfPlayer(lines: List<DailyBoardLine>, player: String) =
        keysOf(lines)[lines.indexOfFirst { it is DailyBoardLine.Real && it.row.playerId == player }]

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
    fun `the same person has the same key on two different days`() {
        val today = rankWithGhosts(listOf(real(1, "ada", 30), real(2, "bo", 40)), listOf(ghost("ref-x", 35)), RankOrder.TimeThenHints)
        val yesterday = rankWithGhosts(listOf(real(1, "bo", 20), real(2, "ada", 45)), listOf(ghost("ref-x", 25)), RankOrder.TimeThenHints)

        for (player in listOf("ada", "bo")) {
            assertEquals(keyOfPlayer(today, player), keyOfPlayer(yesterday, player))
        }
        fun connectionKey(lines: List<DailyBoardLine>) = keysOf(lines)[lines.indexOfFirst { it is DailyBoardLine.Ghost }]
        assertEquals(connectionKey(today), connectionKey(yesterday))
    }

    @Test
    fun `the own row has the same key in the top list and when pinned`() {
        val others = listOf(real(1, "ada", 30), real(2, "bo", 40))
        val inTop = rankWithGhosts(others + real(3, "me", 50, isSelf = true), emptyList(), RankOrder.TimeThenHints)
        val topKey = keysOf(inTop).last()

        val pinnedLines = rankWithGhosts(others, emptyList(), RankOrder.TimeThenHints) + DailyBoardLine.Real(real(9, "me", 90, isSelf = true), 9)
        val pinnedKey = boardKeys(pinnedLines, pinned = true).last()

        assertEquals(topKey, pinnedKey)
    }

    @Test
    fun `the you row has the self key whichever way it got on the board`() {
        val rows = listOf(real(1, "ada", 30), real(2, "Me", 50, isSelf = true))
        val profileRow = keysOf(rankWithGhosts(rows, emptyList(), RankOrder.TimeThenHints)).last()

        val sameName = rankWithLinkedIn(listOf(real(1, "ada", 30), real(2, "Me", 50)), emptyList(), LinkedInYou(BestScore(20_000L, 0), "Me", null), RankOrder.TimeThenHints)
        val sameNameKey = keysOf(sameName)[sameName.indexOfFirst { it is DailyBoardLine.Real && it.row.isSelf }]

        val added = rankWithLinkedIn(listOf(real(1, "ada", 30)), emptyList(), LinkedInYou(BestScore(40_000L, 0), "Someone", null), RankOrder.TimeThenHints)
        val addedKey = keysOf(added)[added.indexOfFirst { it is DailyBoardLine.Real && it.row.fromLinkedIn }]

        assertEquals(profileRow, sameNameKey)
        assertEquals(profileRow, addedKey)
        assertNotEquals(keyOfPlayer(rankWithGhosts(rows, emptyList(), RankOrder.TimeThenHints), "ada"), profileRow)
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
