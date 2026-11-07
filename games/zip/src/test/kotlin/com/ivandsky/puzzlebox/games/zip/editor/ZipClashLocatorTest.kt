package com.ivandsky.puzzlebox.games.zip.editor

import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.Wall
import com.ivandsky.puzzlebox.games.zip.WallDirection
import com.ivandsky.puzzlebox.games.zip.ZipBoard
import org.junit.Assert.assertEquals
import org.junit.Test

/** Hand-checkable boards: a walled-in centre cell, wrong end colours, and a clean split board. */
class ZipClashLocatorTest {

    private fun board(n: Int, waypoints: List<Int>, walls: Set<Wall> = emptySet()) =
        ZipBoard(n, walls, waypoints.map(::Cell))

    private val walledCentre = setOf(
        Wall(Cell(1), WallDirection.DOWN),
        Wall(Cell(3), WallDirection.RIGHT),
        Wall(Cell(4), WallDirection.RIGHT),
        Wall(Cell(4), WallDirection.DOWN),
    )

    private val splitFour = (0 until 4).map { Wall(Cell(it * 4 + 1), WallDirection.RIGHT) }.toSet()

    @Test
    fun `a walled-in cell is named`() {
        assertEquals(setOf(4), ZipClashLocator.conflicts(board(3, listOf(0, 8), walledCentre)))
    }

    @Test
    fun `an endpoint with few open neighbours is not a dead end`() {
        val corner = setOf(Wall(Cell(0), WallDirection.RIGHT), Wall(Cell(0), WallDirection.DOWN))

        assertEquals(emptySet<Int>(), ZipClashLocator.conflicts(board(3, listOf(0, 8), corner)))
    }

    @Test
    fun `a 5x5 board whose ends break parity names both ends`() {
        assertEquals(setOf(0, 1), ZipClashLocator.conflicts(board(5, listOf(0, 12, 1))))
    }

    @Test
    fun `an even board with same-coloured ends names both ends`() {
        assertEquals(setOf(0, 5), ZipClashLocator.conflicts(board(4, listOf(0, 5))))
    }

    @Test
    fun `an unsolvable board with no local cause is empty`() {
        assertEquals(emptySet<Int>(), ZipClashLocator.conflicts(board(4, listOf(0, 14), splitFour)))
    }

    @Test
    fun `the analyzer puts the set into None conflicts`() {
        val draft = ZipDraft(3, walledCentre, emptyList(), listOf(0, 8))

        assertEquals(Verdict.None(setOf(4)), ZipDraftAnalyzer.analyze(draft, Budget.Unlimited))
    }
}
