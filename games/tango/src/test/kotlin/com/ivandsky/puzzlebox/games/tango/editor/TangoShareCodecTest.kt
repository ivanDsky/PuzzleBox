package com.ivandsky.puzzlebox.games.tango.editor

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.DraftExport
import com.ivandsky.puzzlebox.game.editor.EditorShareCodec
import com.ivandsky.puzzlebox.game.editor.GameEditor
import com.ivandsky.puzzlebox.game.editor.SharedOpen
import com.ivandsky.puzzlebox.game.editor.SharedOpenResult
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarker
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.TangoTestSupport
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Share codec (ADR-0020): givens and markers only; 6×6 stays v1, other sizes are v2 with the size
 * byte; unknown versions are typed (ADR-0005).
 */
class TangoShareCodecTest {

    private val sample = TangoDraft(6, TangoTestSupport.sample.givens, TangoTestSupport.sample.markers)

    private fun fixture(): ByteArray =
        TangoShareCodecTest::class.java.getResourceAsStream("/editor/tango-share-v1.bin")!!.use { it.readBytes() }

    /** 8×8, givens at cells ≥ 36 and markers: hand-written from the v2 layout, never re-recorded. */
    private fun fixtureV2(): ByteArray =
        TangoShareCodecTest::class.java.getResourceAsStream("/editor/tango-share-v2.bin")!!.use { it.readBytes() }

    private val board8 = TangoDraft(
        8,
        givens = mapOf(2 to TangoCell.SUN, 37 to TangoCell.MOON, 50 to TangoCell.SUN, 63 to TangoCell.MOON),
        markers = listOf(
            TangoMarker(10, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME),
            TangoMarker(45, TangoEdgeSide.DOWN, TangoMarkerKind.OPPOSITE),
            TangoMarker(54, TangoEdgeSide.RIGHT, TangoMarkerKind.OPPOSITE),
        ),
    )

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
        assertEquals(1, TangoShareCodec.shareFormatVersionOf(sample))
    }

    @Test
    fun `frozen v2 fixture decodes to the 8x8 board`() {
        assertEquals(board8, decodeOk(fixtureV2()))
    }

    @Test
    fun `encoding the 8x8 board reproduces the frozen v2 bytes`() {
        assertArrayEquals(fixtureV2(), TangoShareCodec.encodeShare(board8))
    }

    @Test
    fun `the codec reads up to share format version 2`() {
        assertEquals(2, TangoShareCodec.shareFormatVersion)
    }

    @Test
    fun `a 6x6 board is written as v1 and every other size as v2`() {
        assertEquals(mapOf(4 to 2, 6 to 1, 8 to 2, 10 to 2), TangoDraft.SIZES.associateWith { n ->
            TangoShareCodec.shareFormatVersionOf(TangoDraft(n, emptyMap(), emptyList()))
        })
    }

    @Test
    fun `an empty board of every size round-trips with its version byte`() {
        TangoDraft.SIZES.forEach { n ->
            val empty = TangoDraft(n, emptyMap(), emptyList())
            val bytes = TangoShareCodec.encodeShare(empty)

            assertEquals(TangoShareCodec.shareFormatVersionOf(empty), bytes[0].toInt())
            assertEquals(empty, decodeOk(bytes))
        }
    }

    @Test
    fun `a v2 size outside the editor sizes is an unknown variant`() {
        assertEquals(DecodeError.UnknownVariant("tango/n7"), error(fixtureV2().also { it[1] = 7 }))
        assertEquals(DecodeError.UnknownVariant("tango/n12"), error(fixtureV2().also { it[1] = 12 }))
    }

    @Test
    fun `a v2 6x6 board is malformed because 6x6 is written as v1`() {
        assertMalformed(byteArrayOf(2, 6, 0, 0))
    }

    @Test
    fun `a truncated v2 size is malformed`() {
        assertMalformed(byteArrayOf(2))
    }

    @Test
    fun `a cell off the 8x8 board is malformed`() {
        assertMalformed(byteArrayOf(2, 8, 1, 64, 1, 0))
        assertMalformed(byteArrayOf(2, 8, 0, 1, 64, 0, 0))
    }

    @Test
    fun `a marker off the 8x8 board edge is malformed`() {
        assertMalformed(byteArrayOf(2, 8, 0, 1, 7, 0, 0))
        assertMalformed(byteArrayOf(2, 8, 0, 1, 56, 1, 0))
    }

    @Test
    fun `unordered or repeated v2 entries and trailing v2 bytes are malformed`() {
        assertMalformed(byteArrayOf(2, 8, 2, 37, 2, 2, 1, 0))
        assertMalformed(byteArrayOf(2, 8, 2, 37, 2, 37, 1, 0))
        assertMalformed(byteArrayOf(2, 8, 0, 2, 45, 1, 1, 10, 0, 0))
        assertMalformed(fixtureV2() + byteArrayOf(0))
        assertMalformed(fixtureV2().copyOf(fixtureV2().size - 1))
    }

    /**
     * v0.19.1 reads only v1. A build that cannot read v2 stops at SharedOpen's version check and
     * never decodes the 8×8 bytes, so it shows the Update card: never Malformed, never a 6×6 misread.
     */
    @Test
    fun `a build that reads only v1 opens a v2 share as needs update`() {
        val oldBuild = V1OnlyTangoEditor()

        val result = SharedOpen.verify(oldBuild, TangoShareCodec.shareFormatVersionOf(board8), fixtureV2(), Budget.Unlimited)

        assertEquals(SharedOpenResult.NeedsUpdate(found = 2, maxKnown = 1), result)
        assertEquals(0, oldBuild.decodes)
    }

    @Test
    fun `givens and markers travel in canonical order whatever the draft order`() {
        val shuffled = TangoDraft(
            6,
            givens = sample.givens.entries.reversed().associate { it.key to it.value },
            markers = sample.markers.reversed(),
        )

        assertArrayEquals(fixture(), TangoShareCodec.encodeShare(shuffled))
    }

    @Test
    fun `a version above 2 is an unsupported version`() {
        assertEquals(DecodeError.UnsupportedVersion(found = 3, maxKnown = 2), error(fixture().also { it[0] = 3 }))
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
        val rightOnLastColumn = TangoDraft(6, emptyMap(), listOf(TangoMarker(5, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME)))
        val downOnLastRow = TangoDraft(6, emptyMap(), listOf(TangoMarker(30, TangoEdgeSide.DOWN, TangoMarkerKind.SAME)))

        assertMalformed(TangoShareCodec.encodeShare(rightOnLastColumn))
        assertMalformed(TangoShareCodec.encodeShare(downOnLastRow))
    }

    @Test
    fun `a bad marker side or kind byte is malformed`() {
        val at = fixture().size - 3
        assertMalformed(fixture().also { it[at + 1] = 2 })
        assertMalformed(fixture().also { it[at + 2] = 2 })
    }

    @Test
    fun `an unsorted marker pair is malformed`() {
        val at = fixture().size - 6
        val swapped = fixture().also {
            for (i in 0..2) it[at + i] = it[at + 3 + i].also { _ -> it[at + 3 + i] = it[at + i] }
        }

        assertMalformed(swapped)
    }

    @Test
    fun `a repeated edge is malformed whatever its kind`() {
        val same = byteArrayOf(1, 0, 2, 3, 0, 0, 3, 0, 0)
        val flipped = byteArrayOf(1, 0, 2, 3, 0, 0, 3, 0, 1)

        assertMalformed(same)
        assertMalformed(flipped)
    }

    @Test
    fun `the largest board stays far under the share size cap`() {
        val n = 10
        val full = TangoDraft(
            n,
            givens = (0 until n * n).associateWith { TangoCell.SUN },
            markers = (0 until n * n).flatMap { cell ->
                listOfNotNull(
                    TangoMarker(cell, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME).takeIf { cell % n < n - 1 },
                    TangoMarker(cell, TangoEdgeSide.DOWN, TangoMarkerKind.OPPOSITE).takeIf { cell / n < n - 1 },
                )
            },
        )

        val bytes = TangoShareCodec.encodeShare(full)

        assertEquals(180, full.markers.size)
        assertEquals(1 + 1 + 1 + 2 * 100 + 1 + 3 * 180, bytes.size)
        assertTrue("${bytes.size} bytes", bytes.size < 8192)
        assertEquals(full, decodeOk(bytes))
    }

    /** Stands in for a build whose Tango share codec reads only v1 (v0.19.1): 6×6 bytes only. */
    private class V1OnlyTangoEditor :
        GameEditor<TangoDraft> by TangoEditor,
        DraftExport<TangoDraft> by TangoEditor,
        EditorShareCodec<TangoDraft> {
        var decodes = 0
            private set

        override val shareFormatVersion: Int = 1

        override fun encodeShare(draft: TangoDraft): ByteArray = TangoShareCodec.encodeShare(draft)

        override fun decodeShare(bytes: ByteArray): DecodeResult<TangoDraft> {
            decodes++
            return if (bytes.firstOrNull()?.toInt() == 1) {
                TangoShareCodec.decodeShare(bytes)
            } else {
                DecodeResult.Err(DecodeError.UnsupportedVersion(found = bytes.firstOrNull()?.toInt() ?: 0, maxKnown = 1))
            }
        }
    }
}
