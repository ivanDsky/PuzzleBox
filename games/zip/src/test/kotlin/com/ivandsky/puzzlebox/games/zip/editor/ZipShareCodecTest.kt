package com.ivandsky.puzzlebox.games.zip.editor

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.SharedOpen
import com.ivandsky.puzzlebox.game.editor.SharedOpenResult
import com.ivandsky.puzzlebox.game.editor.canShare
import com.ivandsky.puzzlebox.game.editor.encodeShare
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.Wall
import com.ivandsky.puzzlebox.games.zip.WallDirection
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.ZipTestSupport
import com.ivandsky.puzzlebox.games.zip.codec.ZipCodec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Share codec v1 (ADR-0020): size, walls and waypoints only, so the recipient re-derives the
 * path ([SharedOpen]); unknown versions are typed, never reinterpreted (ADR-0005).
 */
class ZipShareCodecTest {

    private fun fixture(): ByteArray =
        ZipShareCodecTest::class.java.getResourceAsStream("/editor/zip-share-v1.bin")!!.use { it.readBytes() }

    private fun decodeOk(bytes: ByteArray): ZipDraft = (ZipEditor.decodeShare(bytes) as DecodeResult.Ok).value

    private fun error(bytes: ByteArray): DecodeError = (ZipEditor.decodeShare(bytes) as DecodeResult.Err).error

    private fun assertMalformed(bytes: ByteArray) {
        val error = error(bytes)
        assertTrue("expected Malformed, got $error", error is DecodeError.Malformed)
    }

    /** The forced n=5 corridor board the fixture holds, without a path. */
    private val corridor = ZipDraft(
        n = 5,
        walls = ZipTestSupport.n5WallCells.map { Wall(Cell(it), WallDirection.DOWN) }.toSet(),
        path = emptyList(),
        waypoints = listOf(0, 12, 24),
    )

    @Test
    fun `the frozen fixture holds the pinned v1 bytes`() {
        val walls = listOf(0, 1, 2, 3, 6, 7, 8, 9, 10, 11, 12, 13, 16, 17, 18, 19)
        val expected = listOf(1, 5, walls.size) + walls.flatMap { listOf(it, 1) } + listOf(3, 0, 12, 24)

        assertArrayEquals(expected.map { it.toByte() }.toByteArray(), fixture())
    }

    @Test
    fun `frozen v1 fixture decodes to the expected draft with an empty path`() {
        assertEquals(corridor, decodeOk(fixture()))
    }

    @Test
    fun `encoding the fixture board reproduces the frozen bytes`() {
        assertArrayEquals(fixture(), ZipEditor.encodeShare(corridor))
    }

    @Test
    fun `a board of every Zip size with walls on both edges round-trips`() {
        for (variant in ZipEditor.variants) {
            val n = ZipEditor.newDraft(variant).n
            val board = ZipDraft(
                n,
                setOf(Wall(Cell(0), WallDirection.RIGHT), Wall(Cell(0), WallDirection.DOWN), Wall(Cell(n + 1), WallDirection.DOWN)),
                emptyList(),
                listOf(0, n * n - 1, n + 1),
            )

            assertEquals(board, decodeOk(ZipEditor.encodeShare(board)))
        }
    }

    @Test
    fun `the editor advertises share format version 1`() {
        assertEquals(1, ZipEditor.shareFormatVersion)
    }

    @Test
    fun `the path never travels`() {
        val drawn = corridor.copy(path = ZipTestSupport.n5Solution.map { it.idx })

        assertArrayEquals(fixture(), ZipEditor.encodeShare(drawn))
    }

    @Test
    fun `a bumped version byte is an unsupported version`() {
        val bumped = fixture().also { it[0] = 2 }

        assertEquals(DecodeError.UnsupportedVersion(found = 2, maxKnown = 1), error(bumped))
    }

    @Test
    fun `a version byte of zero is malformed`() {
        assertMalformed(fixture().also { it[0] = 0 })
    }

    @Test
    fun `empty and truncated payloads are malformed`() {
        val bytes = fixture()

        assertMalformed(ByteArray(0))
        assertMalformed(bytes.copyOf(1))
        assertMalformed(bytes.copyOf(bytes.size - 1))
    }

    @Test
    fun `trailing bytes are malformed`() {
        assertMalformed(fixture() + byteArrayOf(0))
    }

    @Test
    fun `a size outside the Zip variants is an unknown variant`() {
        assertEquals(DecodeError.UnknownVariant("zip/n9"), error(byteArrayOf(1, 9, 0, 0)))
    }

    @Test
    fun `a wall off the board, on the border, repeated or out of order is malformed`() {
        val right = WallDirection.RIGHT.ordinal.toByte()
        val down = WallDirection.DOWN.ordinal.toByte()

        assertMalformed(byteArrayOf(1, 5, 1, 25, down, 0))
        assertMalformed(byteArrayOf(1, 5, 1, 4, right, 0))
        assertMalformed(byteArrayOf(1, 5, 1, 20, down, 0))
        assertMalformed(byteArrayOf(1, 5, 1, 0, 2, 0))
        assertMalformed(byteArrayOf(1, 5, 2, 0, down, 0, down, 0))
        assertMalformed(byteArrayOf(1, 5, 2, 1, down, 0, down, 0))
    }

    @Test
    fun `a waypoint off the board is malformed`() {
        assertMalformed(byteArrayOf(1, 5, 0, 1, 25))
    }

    @Test
    fun `a Zip draft can share and encodes its current board`() {
        val draft = EditorDraft(ZipEditor, DraftState(corridor))

        assertTrue(draft.canShare)
        assertArrayEquals(fixture(), draft.encodeShare())
    }

    @Test
    fun `the frozen fixture re-verifies into a Zip puzzle that solves to its one path`() {
        val result = SharedOpen.verify(ZipEditor, ZipEditor.shareFormatVersion, fixture(), Budget.Unlimited)

        val verified = result as SharedOpenResult.Verified
        val puzzle = (ZipCodec.decode(verified.puzzle.payload) as DecodeResult.Ok).value
        assertEquals(ZipDefinition.id, verified.puzzle.gameId)
        assertEquals(VariantId("zip/n5"), verified.variant)
        assertEquals(ZipTestSupport.n5Solution, puzzle.solution)
    }

    @Test
    fun `a newer share version on the fixture needs an update`() {
        val result = SharedOpen.verify(ZipEditor, ZipEditor.shareFormatVersion, fixture().also { it[0] = 2 }, Budget.Unlimited)

        assertEquals(SharedOpenResult.NeedsUpdate(found = 2, maxKnown = 1), result)
    }
}
