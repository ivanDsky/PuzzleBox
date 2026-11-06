package com.ivandsky.puzzlebox.games.zipui

import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.SessionBlob
import com.ivandsky.puzzlebox.gameui.BoardPreview
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.codec.ZipSessionCodec
import com.ivandsky.puzzlebox.games.zip.engine.ZipMove
import com.ivandsky.puzzlebox.games.zip.render.ZipRendererModel
import com.ivandsky.puzzlebox.model.GameId
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZipPreviewModelsTest {

    private val ui = ZipPlayUi()

    private fun model(preview: BoardPreview) = ui.previewModel(preview) as ZipRendererModel?

    private fun savedBlob(vararg idx: Int): ByteArray {
        val session = ZipPlayFixture.path(*idx).fold(ZipPlayFixture.session()) { s, cell ->
            s.withMove(ZipMove.Extend(cell), Instant.EPOCH)
        }
        return SessionBlob.encode(ZipDefinition.id, ZipSessionCodec.serialize(session))
    }

    @Test
    fun inProgressRoundTripsTheSavedPath() {
        val model = model(BoardPreview.InProgress(savedBlob(0, 1, 2, 3)))!!
        assertEquals(listOf(0, 1, 2, 3), model.path)
        assertEquals(5, model.n)
    }

    @Test
    fun freshHasNoPath() {
        val model = model(BoardPreview.Fresh(ZipPlayFixture.envelope()))!!
        assertTrue(model.path.isEmpty())
    }

    @Test
    fun solvedCoversTheBoardAndEndsOnTheLastWaypoint() {
        val model = model(BoardPreview.Solved(ZipPlayFixture.envelope()))!!
        assertEquals(25, model.path.toSet().size)
        assertEquals(ZipPlayFixture.waypoints.last().idx, model.path.last())
    }

    @Test
    fun solvedFallsBackToTheSolverWhenNoSolutionShips() {
        val unshipped = ZipPlayFixture.puzzle().copy(solution = null)
        val model = model(BoardPreview.Solved(ZipPlayFixture.envelope(unshipped)))!!
        assertEquals(ZipPlayFixture.solution.map { it.idx }, model.path)
    }

    @Test
    fun unreadableInputIsNull() {
        val session = ZipSessionCodec.serialize(ZipPlayFixture.session())
        assertNull(model(BoardPreview.InProgress(SessionBlob.encode(GameId("tango"), session))))
        assertNull(model(BoardPreview.InProgress(session))) // no envelope
        assertNull(model(BoardPreview.InProgress(byteArrayOf(1, 3, 'z'.code.toByte(), 'i'.code.toByte(), 'p'.code.toByte(), 9))))
        val foreign = ZipPlayFixture.envelope().let { Puzzle(GameId("queens"), it.variant, it.formatVersion, it.payload) }
        assertNull(model(BoardPreview.Fresh(foreign)))
        assertNull(model(BoardPreview.Solved(foreign)))
        assertNull(model(BoardPreview.Fresh(ZipPlayFixture.envelope().let { Puzzle(it.gameId, it.variant, it.formatVersion, byteArrayOf(7)) })))
    }
}
