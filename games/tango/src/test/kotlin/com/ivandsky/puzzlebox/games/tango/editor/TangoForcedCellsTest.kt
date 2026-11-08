package com.ivandsky.puzzlebox.games.tango.editor

import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarker
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TangoForcedCellsTest {
    private val sun = TangoCell.SUN
    private val moon = TangoCell.MOON

    @Test
    fun `a triple completion is returned with its symbol`() {
        val draft = TangoDraft(mapOf(0 to sun, 1 to sun), emptyList())

        assertEquals(mapOf(2 to moon), TangoForcedCells.of(draft))
    }

    @Test
    fun `one forced cell unlocks the next`() {
        val same = TangoMarker(2, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME)
        val draft = TangoDraft(mapOf(0 to sun, 1 to sun), listOf(same))

        assertEquals(mapOf(2 to moon, 3 to moon, 4 to sun, 5 to moon), TangoForcedCells.of(draft))
    }

    @Test
    fun `a given never appears in the result`() {
        val draft = TangoDraft(mapOf(0 to sun, 1 to sun, 2 to moon), emptyList())

        assertTrue(TangoForcedCells.of(draft).keys.none { it in draft.givens })
    }

    @Test
    fun `givens that break a rule force nothing`() {
        val draft = TangoDraft(mapOf(0 to sun, 1 to sun, 2 to sun, 6 to moon, 7 to moon), emptyList())

        assertEquals(emptyMap<Int, TangoCell>(), TangoForcedCells.of(draft))
    }

    @Test
    fun `a board the ladder fully solves returns every empty cell`() {
        val rows = listOf("SMSMMS", "MSMSSM", "SMSMMS", "MSMSSM", "SMSMMS", "MSMSSM")
        val solved = rows.joinToString("").map { if (it == 'S') sun else moon }
        val empty = setOf(0, 14, 35)
        val givens = solved.indices.filter { it !in empty }.associateWith { solved[it] }

        assertEquals(empty.associateWith { solved[it] }, TangoForcedCells.of(TangoDraft(givens, emptyList())))
    }
}
