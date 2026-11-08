package com.ivandsky.puzzlebox.games.tango.editor

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarker
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.TangoTestSupport
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Share codec v1 (ADR-0020): givens and markers only; unknown versions are typed (ADR-0005). */
class TangoShareCodecTest {

    private val sample = TangoDraft(TangoTestSupport.sample.givens, TangoTestSupport.sample.markers)

    private fun fixture(): ByteArray =
        TangoShareCodecTest::class.java.getResourceAsStream("/editor/tango-share-v1.bin")!!.use { it.readBytes() }

    private fun decodeOk(bytes: ByteArray): TangoDraft = (TangoShareCodec.decodeShare(bytes) as DecodeResult.Ok).value

    private fun error(bytes: ByteArray): DecodeError = (TangoShareCodec.decodeShare(bytes) as DecodeResult.Err).error

    private fun assertMalformed(bytes: ByteArray) {
        val error = error(bytes)
        assertTrue("expected Malformed, got $error", error is DecodeError.Malformed)
    }

    @Test
    fun `frozen v1 fixture decodes to the sample board`() {
        assertEquals(sample, decodeOk(fixture()))
    }

    @Test
    fun `encoding the sample board reproduces the frozen bytes`() {
        assertArrayEquals(fixture(), TangoShareCodec.encodeShare(sample))
    }

    @Test
    fun `the codec advertises share format version 1`() {
        assertEquals(1, TangoShareCodec.shareFormatVersion)
    }

    @Test
    fun `givens and markers travel in canonical order whatever the draft order`() {
        val shuffled = TangoDraft(
            givens = sample.givens.entries.reversed().associate { it.key to it.value },
            markers = sample.markers.reversed(),
        )

        assertArrayEquals(fixture(), TangoShareCodec.encodeShare(shuffled))
    }

    @Test
    fun `an empty board round-trips`() {
        val empty = TangoDraft(emptyMap(), emptyList())

        assertEquals(empty, decodeOk(TangoShareCodec.encodeShare(empty)))
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
    fun `trailing bytes are malformed`() {
        assertMalformed(fixture() + byteArrayOf(0))
    }

    @Test
    fun `an off-board given, a bad cell byte and a repeated given are malformed`() {
        assertMalformed(fixture().also { it[2] = 36 })
        assertMalformed(fixture().also { it[3] = 3 })
        assertMalformed(fixture().also { it[4] = 0 })
    }

    @Test
    fun `a marker off the board edge is malformed`() {
        val rightOnLastColumn = TangoDraft(emptyMap(), listOf(TangoMarker(5, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME)))
        val downOnLastRow = TangoDraft(emptyMap(), listOf(TangoMarker(30, TangoEdgeSide.DOWN, TangoMarkerKind.SAME)))

        assertMalformed(TangoShareCodec.encodeShare(rightOnLastColumn))
        assertMalformed(TangoShareCodec.encodeShare(downOnLastRow))
    }

    @Test
    fun `a bad marker side or kind byte is malformed`() {
        val at = fixture().size - 3
        assertMalformed(fixture().also { it[at + 1] = 2 })
        assertMalformed(fixture().also { it[at + 2] = 2 })
    }
}
