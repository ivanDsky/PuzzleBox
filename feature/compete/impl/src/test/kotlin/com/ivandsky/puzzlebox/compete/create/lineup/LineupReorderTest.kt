package com.ivandsky.puzzlebox.compete.create.lineup

import org.junit.Assert.assertEquals
import org.junit.Test

/** Where a dragged row lands, how the others step aside and how far the dropped row has left to go, without a screen. */
class LineupReorderTest {

    private val even = listOf(60f, 60f, 60f)

    @Test
    fun `a row stays in its slot until its leading edge passes the middle of its neighbour`() {
        assertEquals(0, LineupReorder.lift(even, 0).dragged(29f).target)
        assertEquals(1, LineupReorder.lift(even, 0).dragged(31f).target)
        assertEquals(2, LineupReorder.lift(even, 1).dragged(31f).target)
        assertEquals(0, LineupReorder.lift(even, 1).dragged(-31f).target)
    }

    @Test
    fun `dragged past either end of the list the row stops there and lands in the first or last slot`() {
        val down = LineupReorder.lift(even, 0).dragged(1_000f)
        assertEquals(120f, down.offset)
        assertEquals(2, down.target)

        val up = LineupReorder.lift(even, 2).dragged(-1_000f)
        assertEquals(-120f, up.offset)
        assertEquals(0, up.target)
    }

    @Test
    fun `a drag is the sum of its steps`() {
        assertEquals(50f, LineupReorder.lift(even, 1).dragged(20f).dragged(30f).offset)
    }

    @Test
    fun `rows of different heights are crossed by their own middles`() {
        val uneven = listOf(40f, 100f, 60f)

        assertEquals(0, LineupReorder.lift(uneven, 0).dragged(49f).target)
        assertEquals(1, LineupReorder.lift(uneven, 0).dragged(51f).target)
        assertEquals(2, LineupReorder.lift(uneven, 0).dragged(131f).target)
    }

    @Test
    fun `rows between the lifted one and its target step aside by its height, the lifted one follows the finger`() {
        val down = LineupReorder.lift(even, 0).dragged(120f)
        assertEquals(listOf(120f, -60f, -60f), (0..2).map(down::shiftOf))

        val up = LineupReorder.lift(listOf(60f, 60f, 60f, 60f), 3).dragged(-130f)
        assertEquals(listOf(0f, 60f, 60f, -130f), (0..3).map(up::shiftOf))
    }

    @Test
    fun `a row that has not left its slot moves nothing`() {
        val still = LineupReorder.lift(even, 1).dragged(10f)

        assertEquals(listOf(0f, 10f, 0f), (0..2).map(still::shiftOf))
    }

    @Test
    fun `once dropped the row has left what the finger put between it and its slot`() {
        assertEquals(10f, LineupReorder.lift(even, 0).dragged(70f).settleFrom)
        assertEquals(20f, LineupReorder.lift(even, 2).dragged(-100f).settleFrom)
        assertEquals(15f, LineupReorder.lift(even, 1).dragged(15f).settleFrom)
    }
}
