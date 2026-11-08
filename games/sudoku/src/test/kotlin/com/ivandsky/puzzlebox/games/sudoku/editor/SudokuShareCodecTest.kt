package com.ivandsky.puzzlebox.games.sudoku.editor

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.games.sudoku.KnownSolutions
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Share codec v1 (ADR-0020): size and givens only, so the recipient re-derives the solution;
 * unknown versions are typed, never reinterpreted (ADR-0005).
 */
class SudokuShareCodecTest {

    private fun fixture(): ByteArray =
        SudokuShareCodecTest::class.java.getResourceAsStream("/editor/sudoku-share-v1.bin")!!.use { it.readBytes() }

    private val fixtureDraft = SudokuDraft(6, KnownSolutions.grid(KnownSolutions.MINI_SAMPLE_GIVENS))

    private fun decodeOk(bytes: ByteArray): SudokuDraft = (SudokuShareCodec.decodeShare(bytes) as DecodeResult.Ok).value

    private fun error(bytes: ByteArray): DecodeError = (SudokuShareCodec.decodeShare(bytes) as DecodeResult.Err).error

    private fun assertMalformed(bytes: ByteArray) {
        val error = error(bytes)
        assertTrue("expected Malformed, got $error", error is DecodeError.Malformed)
    }

    @Test
    fun `frozen v1 fixture decodes to the expected givens`() {
        assertEquals(fixtureDraft, decodeOk(fixture()))
    }

    @Test
    fun `encoding the fixture board reproduces the frozen bytes`() {
        assertArrayEquals(fixture(), SudokuShareCodec.encodeShare(fixtureDraft))
    }

    @Test
    fun `the codec advertises share format version 1`() {
        assertEquals(1, SudokuShareCodec.shareFormatVersion)
    }

    @Test
    fun `a board of every size round-trips`() {
        for (size in SudokuDraft.SIZES) {
            val board = SudokuDraft(size, List(size * size) { it % (size + 1) })
            assertEquals(board, decodeOk(SudokuShareCodec.encodeShare(board)))
        }
    }

    @Test
    fun `a bumped version byte is an unsupported version`() {
        assertEquals(DecodeError.UnsupportedVersion(found = 2, maxKnown = 1), error(fixture().also { it[0] = 2 }))
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
    fun `a digit above the board size is malformed`() {
        assertMalformed(fixture().also { it[2] = 7 })
    }

    @Test
    fun `trailing bytes are malformed`() {
        assertMalformed(fixture() + byteArrayOf(0))
    }

    @Test
    fun `a size outside the shipped variants is an unknown variant`() {
        val board = byteArrayOf(1, 7) + ByteArray(49)

        assertEquals(DecodeError.UnknownVariant("sudoku/7x7"), error(board))
    }
}
