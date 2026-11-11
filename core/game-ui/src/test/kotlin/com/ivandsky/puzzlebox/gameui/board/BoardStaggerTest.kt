package com.ivandsky.puzzlebox.gameui.board

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When each row starts to move: reordered rows one after another, rows that only shift not at all. */
class BoardStaggerTest {
    private fun real(player: String) =
        DailyBoardLine.Real(DailyBoardRowModel(1, player, 10_000L, 0, isSelf = false, playerId = player), 1)

    private fun lines(vararg players: String) = players.map(::real)

    private fun List<BoardItem>.delayOf(player: String) = first { it.key == "player:$player" }.delayMs

    @Test
    fun `the stagger is 70 ms a row, capped at the eighth`() {
        assertEquals(0, BoardMotion.staggerMs(0))
        assertEquals(70, BoardMotion.staggerMs(1))
        assertEquals(560, BoardMotion.staggerMs(8))
        assertEquals(560, BoardMotion.staggerMs(30))
    }

    @Test
    fun `the k-th reordered row waits k times 70 ms in the new order, up to the cap`() {
        val names = ('a'..'l').map { "$it" }
        val rows = BoardRows()
        rows.update(lines(*names.toTypedArray()), null)

        val items = rows.update(lines(*names.reversed().toTypedArray()), null)

        names.reversed().forEachIndexed { k, name -> assertEquals("row $name", minOf(k, 8) * 70, items.delayOf(name)) }
    }

    @Test
    fun `rows that only shift because others open or close wait nothing`() {
        val rows = BoardRows()
        rows.update(lines("a", "b", "c"), null)

        val grown = rows.update(lines("x", "a", "b", "c"), null)
        listOf("a", "b", "c").forEach { assertEquals("$it only shifts", 0, grown.delayOf(it)) }

        val shrunk = rows.update(lines("a", "c"), null)
        listOf("a", "c").forEach { assertEquals("$it only shifts", 0, shrunk.delayOf(it)) }
    }

    @Test
    fun `an arriving row waits the entrance delay and shares the counter with the reordered rows`() {
        val rows = BoardRows()
        rows.update(lines("a", "b", "c", "d"), null)

        val items = rows.update(lines("x", "b", "a", "c", "d"), null)

        assertEquals("x arrives first in the new order", 200, items.delayOf("x"))
        assertEquals(70, items.delayOf("b"))
        assertEquals(140, items.delayOf("a"))
        assertEquals(0, items.delayOf("c"))
        assertEquals(0, items.delayOf("d"))
    }

    @Test
    fun `the rows of the first list are not arrivals and wait nothing`() {
        val rows = BoardRows()

        val items = rows.update(lines("a", "b", "c"), null)

        assertTrue(items.all { it.delayMs == 0 })
        assertFalse(items.any { it.arrived })
    }

    @Test
    fun `a row added later is an arrival, and so is one on a day that follows an empty one`() {
        val rows = BoardRows()
        rows.update(lines("a"), null)
        assertTrue(rows.update(lines("a", "b"), null).first { it.key == "player:b" }.arrived)

        rows.update(emptyList(), null)
        assertTrue(rows.update(lines("z"), null).first { it.key == "player:z" }.arrived)
    }
}
