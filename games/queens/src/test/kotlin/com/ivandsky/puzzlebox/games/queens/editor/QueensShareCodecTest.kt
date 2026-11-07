package com.ivandsky.puzzlebox.games.queens.editor

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.SharedOpen
import com.ivandsky.puzzlebox.game.editor.SharedOpenResult
import com.ivandsky.puzzlebox.game.editor.canShare
import com.ivandsky.puzzlebox.game.editor.encodeShare
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensDefinition
import com.ivandsky.puzzlebox.games.queens.QueensVariants
import com.ivandsky.puzzlebox.games.queens.codec.QueensCodec
import com.ivandsky.puzzlebox.games.queens.editor.QueensDraft.Companion.UNPAINTED
import com.ivandsky.puzzlebox.games.queens.queensVariantId
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Share codec v1 (ADR-0020): size and per-cell regions only, so the recipient re-derives the
 * solution ([SharedOpen]); unknown versions are typed, never reinterpreted (ADR-0005).
 */
class QueensShareCodecTest {

    private fun fixture(): ByteArray =
        QueensShareCodecTest::class.java.getResourceAsStream("/editor/queens-share-v1.bin")!!.use { it.readBytes() }

    private fun decodeOk(bytes: ByteArray): QueensDraft = (QueensEditor.decodeShare(bytes) as DecodeResult.Ok).value

    private fun error(bytes: ByteArray): DecodeError = (QueensEditor.decodeShare(bytes) as DecodeResult.Err).error

    private fun assertMalformed(bytes: ByteArray) {
        val error = error(bytes)
        assertTrue("expected Malformed, got $error", error is DecodeError.Malformed)
    }

    /** Every cell painted with its row as the region: a full board of any size. */
    private fun striped(size: Int) = QueensDraft(size, List(size * size) { it / size })

    @Test
    fun `a painted board of every Queens variant round-trips`() {
        for (size in QueensVariants.MIN_SIZE..QueensVariants.MAX_SIZE) {
            val board = striped(size)
            assertEquals(board, decodeOk(QueensEditor.encodeShare(board)))
        }
        assertEquals(QueensVariants.all.map { it.id }, QueensEditor.variants)
    }

    @Test
    fun `frozen v1 fixture decodes to the expected regions`() {
        assertEquals(QueensDraft(7, FIXTURE_REGIONS), decodeOk(fixture()))
    }

    @Test
    fun `encoding the fixture board reproduces the frozen bytes`() {
        assertArrayEquals(fixture(), QueensEditor.encodeShare(QueensDraft(7, FIXTURE_REGIONS)))
    }

    @Test
    fun `the editor advertises share format version 1`() {
        assertEquals(1, QueensEditor.shareFormatVersion)
    }

    @Test
    fun `answer crowns never travel`() {
        val crowned = QueensDraft(7, FIXTURE_REGIONS, crowns = setOf(0, 11, 15))

        assertArrayEquals(fixture(), QueensEditor.encodeShare(crowned))
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
    fun `an unpainted cell is malformed`() {
        val partial = QueensDraft(7, FIXTURE_REGIONS.toMutableList().also { it[20] = UNPAINTED })

        assertMalformed(QueensEditor.encodeShare(partial))
    }

    @Test
    fun `a region id outside the board is malformed`() {
        assertMalformed(fixture().also { it[2] = 7 })
    }

    @Test
    fun `trailing bytes are malformed`() {
        assertMalformed(fixture() + byteArrayOf(0))
    }

    @Test
    fun `a size outside the shipped variants is an unknown variant`() {
        val board = byteArrayOf(1, 13) + ByteArray(13 * 13)

        assertEquals(DecodeError.UnknownVariant(queensVariantId(13).value), error(board))
    }

    @Test
    fun `a Queens draft can share and encodes its current board`() {
        val draft = EditorDraft(QueensEditor, DraftState(QueensDraft(7, FIXTURE_REGIONS)))

        assertTrue(draft.canShare)
        assertArrayEquals(fixture(), draft.encodeShare())
    }

    @Test
    fun `the frozen fixture re-verifies into a puzzle that solves to its one solution`() {
        val result = SharedOpen.verify(QueensEditor, QueensEditor.shareFormatVersion, fixture(), Budget.Unlimited)

        val verified = result as SharedOpenResult.Verified
        val puzzle = (QueensCodec.decode(verified.puzzle.payload) as DecodeResult.Ok).value
        assertEquals(QueensDefinition.id, verified.puzzle.gameId)
        assertEquals(queensVariantId(7), verified.variant)
        assertEquals(FIXTURE_REGIONS, puzzle.regions)
        assertEquals(FIXTURE_SOLUTION, puzzle.solution?.sortedBy { it.idx })
    }

    @Test
    fun `a newer share version on the fixture needs an update`() {
        val result = SharedOpen.verify(QueensEditor, QueensEditor.shareFormatVersion, fixture().also { it[0] = 2 }, Budget.Unlimited)

        assertEquals(SharedOpenResult.NeedsUpdate(found = 2, maxKnown = 1), result)
    }

    private companion object {
        /** What `queens-share-v1.bin` holds after its `[1][7]` header: a unique 7×7 board. */
        val FIXTURE_REGIONS = listOf(
            0, 2, 2, 2, 1, 3, 3,
            0, 2, 2, 2, 1, 3, 3,
            2, 2, 3, 3, 3, 3, 3,
            3, 3, 3, 3, 3, 3, 4,
            3, 3, 3, 3, 3, 4, 4,
            5, 5, 5, 5, 5, 5, 6,
            5, 5, 5, 5, 5, 5, 6,
        )

        /** Its only queen placement, row by row: columns 0, 4, 1, 3, 5, 2, 6. */
        val FIXTURE_SOLUTION = listOf(0, 11, 15, 24, 33, 37, 48).map(::Cell)
    }
}
