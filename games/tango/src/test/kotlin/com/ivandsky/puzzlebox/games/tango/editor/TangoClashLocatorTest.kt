package com.ivandsky.puzzlebox.games.tango.editor

import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarker
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import org.junit.Assert.assertEquals
import org.junit.Test

class TangoClashLocatorTest {

    private fun puzzle(givens: Map<Int, TangoCell>, markers: List<TangoMarker> = emptyList()) =
        TangoPuzzle(VariantId("tango/n6"), 6, givens, markers)

    @Test
    fun `no givens break nothing`() {
        assertEquals(emptySet<Int>(), TangoClashLocator.conflicts(puzzle(mapOf(0 to TangoCell.SUN, 1 to TangoCell.MOON))))
    }

    @Test
    fun `a same marker between a sun and a moon names both cells`() {
        val marker = TangoMarker(7, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME)

        val cells = TangoClashLocator.conflicts(puzzle(mapOf(7 to TangoCell.SUN, 8 to TangoCell.MOON), listOf(marker)))

        assertEquals(setOf(7, 8), cells)
    }

    @Test
    fun `an opposite marker between equal cells names both cells`() {
        val marker = TangoMarker(7, TangoEdgeSide.DOWN, TangoMarkerKind.OPPOSITE)

        val cells = TangoClashLocator.conflicts(puzzle(mapOf(7 to TangoCell.SUN, 13 to TangoCell.SUN), listOf(marker)))

        assertEquals(setOf(7, 13), cells)
    }

    @Test
    fun `a marker with one empty end is no clash`() {
        val marker = TangoMarker(7, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME)

        assertEquals(emptySet<Int>(), TangoClashLocator.conflicts(puzzle(mapOf(7 to TangoCell.SUN), listOf(marker))))
    }

    @Test
    fun `three in a row across a row names the three cells`() {
        val cells = TangoClashLocator.conflicts(puzzle((7..9).associateWith { TangoCell.MOON }))

        assertEquals(setOf(7, 8, 9), cells)
    }

    @Test
    fun `three in a row down a column names the three cells`() {
        val cells = TangoClashLocator.conflicts(puzzle(listOf(1, 7, 13).associateWith { TangoCell.SUN }))

        assertEquals(setOf(1, 7, 13), cells)
    }

    @Test
    fun `too many of one symbol in a line names every one of them`() {
        val suns = listOf(0, 1, 3, 4)

        val cells = TangoClashLocator.conflicts(puzzle(suns.associateWith { TangoCell.SUN } + (2 to TangoCell.MOON)))

        assertEquals(suns.toSet(), cells)
    }

    @Test
    fun `the analyzer returns None carrying the clash cells`() {
        val draft = TangoDraft(6, (7..9).associateWith { TangoCell.MOON }, emptyList())

        assertEquals(Verdict.None(setOf(7, 8, 9)), TangoDraftAnalyzer.analyze(draft, Budget.Unlimited))
    }

    @Test
    fun `the analyzer reports a clash without spending the budget`() {
        val draft = TangoDraft(6, (7..9).associateWith { TangoCell.MOON }, emptyList())

        assertEquals(Verdict.None(setOf(7, 8, 9)), TangoDraftAnalyzer.analyze(draft) { true })
    }
}
