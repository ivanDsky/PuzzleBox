package com.ivandsky.puzzlebox.games.queensui

import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.gameui.BoardPreview
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.QueensSession
import com.ivandsky.puzzlebox.games.queens.codec.QueensCodec
import com.ivandsky.puzzlebox.games.queens.codec.QueensSessionCodec
import com.ivandsky.puzzlebox.games.queens.render.QueensRendererModel
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QueensPreviewModelTest {

    private val ui = QueensPlayUi()

    private fun model(preview: BoardPreview) = ui.previewModel(preview) as QueensRendererModel?

    @Test
    fun `a save keeps its placed queens`() {
        val session = listOf(0, 12).fold(QueensSession(ref = QueensPlayFixtures.ref(), puzzle = QueensPlayFixtures.puzzle())) { s, i ->
            s.withQueenPlaced(Cell(i), QueensUiFixtures.T0)
        }
        val bytes = SessionBlob.encode(QueensDefinition.id, QueensSessionCodec.serialize(session))
        val m = model(BoardPreview.InProgress(bytes))!!
        assertEquals(setOf(0, 12), m.queens)
        assertEquals(8, m.n)
        assertEquals(null, m.hintHighlight)
    }

    @Test
    fun `a fresh puzzle has no queens`() {
        val m = model(BoardPreview.Fresh(QueensPlayFixtures.envelope()))!!
        assertTrue(m.queens.isEmpty())
        assertEquals(QueensPlayFixtures.puzzle().regions, m.regions)
    }

    @Test
    fun `a solved puzzle shows its solution`() {
        val m = model(BoardPreview.Solved(QueensPlayFixtures.envelope()))!!
        assertEquals(QueensPlayFixtures.solution.map { it.idx }.toSet(), m.queens)
        assertEquals(QueensRendererModel.Status.COMPLETE, m.status)
    }

    @Test
    fun `a solved puzzle without a shipped solution is solved here`() {
        val bare = QueensPlayFixtures.puzzle().copy(solution = null)
        val envelope = Puzzle(QueensDefinition.id, VariantId("queens/n8"), QueensCodec.VERSION, QueensCodec.encode(bare))
        val m = model(BoardPreview.Solved(envelope))!!
        assertEquals(8, m.queens.size)
        assertEquals(QueensRendererModel.Status.COMPLETE, m.status)
    }

    @Test
    fun `unreadable or foreign input gives no model`() {
        val other = GameId("zip")
        val session = QueensSessionCodec.serialize(QueensSession(ref = null, puzzle = QueensPlayFixtures.puzzle()))
        assertNull(ui.previewModel(BoardPreview.InProgress(byteArrayOf(1, 2, 3))))
        assertNull(ui.previewModel(BoardPreview.InProgress(SessionBlob.encode(other, session))))
        assertNull(ui.previewModel(BoardPreview.InProgress(SessionBlob.encode(QueensDefinition.id, byteArrayOf(9, 9)))))
        val envelope = QueensPlayFixtures.envelope()
        val foreign = Puzzle(other, VariantId("zip/n6"), 1, envelope.payload)
        assertNull(ui.previewModel(BoardPreview.Fresh(foreign)))
        assertNull(ui.previewModel(BoardPreview.Solved(foreign)))
        assertNull(ui.previewModel(BoardPreview.Fresh(Puzzle(QueensDefinition.id, envelope.variant, 1, byteArrayOf(7)))))
    }
}
