package com.ivandsky.puzzlebox.games.tangoui

import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoSession
import com.ivandsky.puzzlebox.games.tango.codec.TangoSessionCodec
import com.ivandsky.puzzlebox.games.tango.render.TangoRendererModel
import com.ivandsky.puzzlebox.gameui.BoardPreview
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The hub card's Tango thumbnail model ([TangoPlayUi.previewModel]): save, fresh day, solved day, and refusals. */
class TangoThumbnailTest {

    private val ui = TangoPlayUi()
    private val puzzle = TangoPlayFixture.puzzle

    private fun model(preview: BoardPreview): TangoRendererModel =
        ui.previewModel(preview).also(::assertNotNull) as TangoRendererModel

    private fun givensOnly(): List<TangoCell?> = List(puzzle.cellCount) { puzzle.givens[it] }

    @Test
    fun `an in-progress save keeps the placed cells over the givens`() {
        val placements = TangoPlayFixture.placements.take(4).toMap()
        val session = TangoSessionCodec.serialize(TangoSession(ref = null, puzzle = puzzle, placements = placements))
        val bytes = SessionBlob.encode(TangoDefinition.id, session)

        val cells = model(BoardPreview.InProgress(bytes)).cells

        val expected = givensOnly().mapIndexed { idx, given -> given ?: placements[idx] }
        assertEquals(expected, cells.map { it.value })
        placements.keys.forEach { idx -> assertEquals(false, cells[idx].isGiven) }
        assertEquals(TangoRendererModel.Status.PLAYING, model(BoardPreview.InProgress(bytes)).status)
    }

    @Test
    fun `a fresh day shows the givens only`() {
        val fresh = model(BoardPreview.Fresh(TangoPlayFixture.envelope()))

        assertEquals(givensOnly(), fresh.cells.map { it.value })
        assertEquals(TangoRendererModel.Status.IDLE, fresh.status)
        assertEquals(puzzle.markers.size, fresh.markers.size)
    }

    @Test
    fun `a solved day fills every cell and keeps the givens`() {
        val solved = model(BoardPreview.Solved(TangoPlayFixture.envelope()))

        assertTrue(solved.cells.none { it.value == null })
        assertEquals(TangoPlayFixture.solution, solved.cells.map { it.value })
        puzzle.givens.forEach { (idx, value) ->
            assertEquals(value, solved.cells[idx].value)
            assertTrue(solved.cells[idx].isGiven)
        }
        assertEquals(TangoRendererModel.Status.COMPLETE, solved.status)
    }

    @Test
    fun `foreign bytes and another game's puzzle read as null`() {
        val envelope = TangoPlayFixture.envelope()
        val foreign = Puzzle(GameId("zip"), envelope.variant, envelope.formatVersion, envelope.payload)
        val garbage = Puzzle(envelope.gameId, envelope.variant, envelope.formatVersion, byteArrayOf(9, 9, 9))

        assertNull(ui.previewModel(BoardPreview.InProgress(byteArrayOf(42, 1, 2, 3))))
        assertNull(ui.previewModel(BoardPreview.InProgress(ByteArray(0))))
        val session = TangoSessionCodec.serialize(TangoSession(ref = null, puzzle = puzzle, placements = emptyMap()))
        assertNull(ui.previewModel(BoardPreview.InProgress(SessionBlob.encode(GameId("zip"), session))))
        assertNull(ui.previewModel(BoardPreview.InProgress(session)))
        assertNull(ui.previewModel(BoardPreview.Fresh(foreign)))
        assertNull(ui.previewModel(BoardPreview.Solved(foreign)))
        assertNull(ui.previewModel(BoardPreview.Fresh(garbage)))
    }

    @Test
    fun `an ambiguous day has no solved thumbnail`() {
        assertNull(ui.previewModel(BoardPreview.Solved(TangoPlayFixture.envelope(TangoPlayFixture.ambiguous))))
    }
}
