package com.ivandsky.puzzlebox.games.queens.editor

import com.ivandsky.puzzlebox.games.queens.editor.QueensDraft.Companion.UNPAINTED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Answer-crown rules for authoring (flow C, round 6): crossed cells, seeding, refusal, removal. */
class QueensCrownsTest {

    private fun blank(size: Int, crowns: Set<Int> = emptySet()) =
        QueensDraft(size, List(size * size) { UNPAINTED }, crowns)

    /** Row ∪ column ∪ 8 neighbours of every crown, minus the crowns, computed by coordinates. */
    private fun expectedCrossed(size: Int, crowns: Set<Int>): Set<Int> =
        (0 until size * size).filter { cell ->
            cell !in crowns && crowns.any { c ->
                val dr = Math.abs(c / size - cell / size)
                val dc = Math.abs(c % size - cell % size)
                dr == 0 || dc == 0 || (dr <= 1 && dc <= 1)
            }
        }.toSet()

    @Test
    fun `no crowns cross nothing`() {
        assertEquals(emptySet<Int>(), QueensCrowns.crossed(blank(5)))
    }

    @Test
    fun `one crown crosses its row, column and eight neighbours only`() {
        // 5×5, crown at (2,2) = cell 12.
        val crossed = QueensCrowns.crossed(blank(5, setOf(12)))

        val row = setOf(10, 11, 13, 14)
        val column = setOf(2, 7, 17, 22)
        val diagonals = setOf(6, 8, 16, 18)
        assertEquals(row + column + diagonals, crossed)
    }

    @Test
    fun `a corner crown clips its neighbours at the edge`() {
        assertEquals(setOf(1, 2, 3, 4, 8, 12, 5), QueensCrowns.crossed(blank(4, setOf(0))))
    }

    @Test
    fun `several crowns cross the union and never a crown cell`() {
        for (crowns in listOf(setOf(1, 15), setOf(0, 13, 26, 39), setOf(5, 30))) {
            val size = if (crowns.max() < 16) 4 else 7
            assertEquals(expectedCrossed(size, crowns), QueensCrowns.crossed(blank(size, crowns)))
        }
    }

    @Test
    fun `a tap on a crossed cell is refused`() {
        val draft = blank(5, setOf(12))

        for (cell in QueensCrowns.crossed(draft)) assertNull(QueensCrowns.toggle(draft, cell))
    }

    @Test
    fun `a tap on an open unpainted cell places with the lowest region no crown holds`() {
        // Crowns at 0 (region 0) and 7 (region 2) on 5×5; cell 14 is open and unpainted.
        val regions = List(25) { UNPAINTED }.toMutableList().apply {
            this[0] = 0
            this[7] = 2
        }
        val draft = QueensDraft(5, regions, setOf(0, 7))

        assertEquals(QueensDraftCommand.Crown(14, placed = true, seed = 1), QueensCrowns.toggle(draft, 14))
    }

    @Test
    fun `the first crown on a blank board seeds region zero`() {
        assertEquals(QueensDraftCommand.Crown(6, placed = true, seed = 0), QueensCrowns.toggle(blank(4), 6))
    }

    @Test
    fun `a tap on an open painted cell places without a seed`() {
        val draft = QueensDraft(4, List(16) { UNPAINTED }.toMutableList().apply { this[9] = 3 })

        assertEquals(QueensDraftCommand.Crown(9, placed = true, seed = null), QueensCrowns.toggle(draft, 9))
    }

    @Test
    fun `a tap on a crown removes it without a seed and its paint stays`() {
        val placed = QueensCrowns.toggle(blank(4), 6)!!.apply(blank(4))

        val remove = QueensCrowns.toggle(placed, 6)!!
        val removed = remove.apply(placed)

        assertEquals(QueensDraftCommand.Crown(6, placed = false, seed = null), remove)
        assertEquals(emptySet<Int>(), removed.crowns)
        assertEquals(0, removed.regions[6])
    }
}
