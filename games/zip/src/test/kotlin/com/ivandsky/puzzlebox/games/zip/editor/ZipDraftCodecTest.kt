package com.ivandsky.puzzlebox.games.zip.editor

import com.ivandsky.puzzlebox.game.CommandHistory
import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.ReplaceDraft
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.Wall
import com.ivandsky.puzzlebox.games.zip.WallDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZipDraftCodecTest {

    private fun blank(n: Int) = ZipDraft(n, emptySet(), emptyList(), emptyList())

    private fun drawn() = ZipDraft(
        n = 5,
        walls = setOf(Wall(Cell(7), WallDirection.DOWN), Wall(Cell(2), WallDirection.RIGHT)),
        path = listOf(0, 1, 2, 7, 12),
        waypoints = listOf(0, 7, 12),
    )

    private fun decodeOk(bytes: ByteArray): DraftState<ZipDraft> =
        (ZipDraftCodec.decode(bytes) as DecodeResult.Ok).value

    private fun malformed(bytes: ByteArray) {
        val result = ZipDraftCodec.decode(bytes)
        assertTrue("expected Malformed, got $result", result is DecodeResult.Err && result.error is DecodeError.Malformed)
    }

    private fun withHistory(): DraftState<ZipDraft> {
        val resize = ReplaceDraft(blank(6), drawn())
        val redo = ReplaceDraft(drawn(), blank(7))
        return DraftState(drawn(), CommandHistory(listOf(resize), listOf(redo)))
    }

    @Test
    fun `a draft round-trips with both history stacks`() {
        val state = withHistory()
        assertEquals(state, decodeOk(ZipDraftCodec.encode(state)))
    }

    @Test
    fun `an empty path and no history round-trip`() {
        val state = DraftState(blank(8))
        assertEquals(state, decodeOk(ZipDraftCodec.encode(state)))
    }

    @Test
    fun `an unknown version decodes to UnsupportedVersion`() {
        val bytes = ZipDraftCodec.encode(withHistory()).also { it[0] = 2 }
        assertEquals(DecodeResult.Err(DecodeError.UnsupportedVersion(2, ZipDraftCodec.VERSION)), ZipDraftCodec.decode(bytes))
    }

    @Test
    fun `history beyond the bound keeps the newest steps`() {
        val steps = List(ZipDraftCodec.MAX_HISTORY + 1) { i -> ReplaceDraft(blank(5), blank(5).copy(path = listOf(i % 25))) }
        val decoded = decodeOk(ZipDraftCodec.encode(DraftState(blank(5), CommandHistory(steps, steps))))
        assertEquals(steps.drop(1), decoded.history.done)
        assertEquals(steps.drop(1), decoded.history.undone)
    }

    @Test
    fun `empty, truncated or trailing bytes are malformed`() {
        val bytes = ZipDraftCodec.encode(withHistory())
        malformed(ByteArray(0))
        malformed(bytes.copyOf(bytes.size - 1))
        malformed(bytes + 0)
    }

    @Test
    fun `an unsupported size is rejected`() {
        val bytes = ZipDraftCodec.encode(DraftState(blank(5))).also { it[1] = 12 }
        val result = ZipDraftCodec.decode(bytes)
        assertTrue(result is DecodeResult.Err && result.error is DecodeError.UnknownVariant)
    }

    @Test
    fun `a step that does not meet the board size is malformed`() {
        val state = DraftState(blank(5), CommandHistory(listOf(ReplaceDraft(blank(6), blank(7)))))
        malformed(ZipDraftCodec.encode(state))
    }

    @Test
    fun `a path cell off the board is malformed`() {
        val bytes = ZipDraftCodec.encode(DraftState(blank(5).copy(path = listOf(3))))
        bytes[bytes.size - 5] = 25 // path cell: [path 1][cell][waypoints 0][done u16][undone u16]
        malformed(bytes)
    }

    @Test
    fun `an unknown step tag is malformed`() {
        val bytes = ZipDraftCodec.encode(withHistory())
        val tagAt = 1 + boardSize(drawn()) + 2
        bytes[tagAt] = 0x7F
        malformed(bytes)
    }

    private fun boardSize(d: ZipDraft) = 1 + 1 + d.walls.size * 2 + 1 + d.path.size + 1 + d.waypoints.size
}
