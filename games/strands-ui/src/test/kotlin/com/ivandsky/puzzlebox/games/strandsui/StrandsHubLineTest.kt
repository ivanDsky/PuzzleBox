package com.ivandsky.puzzlebox.games.strandsui

import com.ivandsky.puzzlebox.designsystem.AccentSlot
import com.ivandsky.puzzlebox.gameui.BoardPreview
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.games.strands.StrandsDefinition
import com.ivandsky.puzzlebox.games.strands.StrandsSession
import com.ivandsky.puzzlebox.games.strands.StrandsState
import com.ivandsky.puzzlebox.games.strands.codec.StrandsSessionCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StrandsHubLineTest {
    private val ui = StrandsPlayUi()
    private val puzzle = StrandsUiFixtures.puzzle

    private fun inProgress(found: List<String>) = BoardPreview.InProgress(
        StrandsSessionCodec.serialize(StrandsSession(StrandsUiFixtures.ref, StrandsState(puzzle, found = found))),
    )

    @Test
    fun registryBrandsStrandsPinkWithItsGlyph() {
        val brand = GamePlayRegistry(listOf(ui)).brandOf(StrandsDefinition.id)
        assertEquals(AccentSlot.Pink, brand.accent)
        assertSame(StrandsGlyph, brand.glyph)
    }

    @Test
    fun freshShowsTheClue() {
        assertEquals(puzzle.clue, ui.hubLine(BoardPreview.Fresh(StrandsUiFixtures.envelope())))
    }

    @Test
    fun inProgressCountsFoundAgainstTotal() {
        val line = ui.hubLine(inProgress(listOf("PIES", "BAKE")))
        assertNotNull(line)
        assertTrue(line!!.contains("2") && line.contains("${puzzle.totalWords}"))
        assertTrue(ui.hubLine(inProgress(listOf("PIES"))) != line)
    }

    @Test
    fun solvedAndCorruptShowNothing() {
        assertNull(ui.hubLine(BoardPreview.Solved(StrandsUiFixtures.envelope())))
        assertNull(ui.hubLine(BoardPreview.InProgress(byteArrayOf(1, 2, 3))))
    }
}
