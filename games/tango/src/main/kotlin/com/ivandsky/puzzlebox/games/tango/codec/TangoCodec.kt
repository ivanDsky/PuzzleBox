package com.ivandsky.puzzlebox.games.tango.codec

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarker
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.TangoPuzzle
import java.io.ByteArrayOutputStream

/**
 * Tango puzzle codec v1 — the ZipCodec reader rules applied to the Tango board (A19/ADR-0005).
 * Little-endian; u8 unless noted:
 *
 * ```
 * 0   u8   formatVersion = 1
 * 1   u8   variantKeyLen; UTF-8 bytes        ("tango/n6" stored, never derived from n)
 *     u8   n                                 (2..11 structural; variant must match, I9)
 *     u8   givenCount; x (u8 cellIdx, u8 value)          (value: 1=SUN, 2=MOON; ascending idx)
 *     u8   markerCount; x (u8 cellIdx, u8 side, u8 kind) (side: 0=RIGHT, 1=DOWN; kind: 0='=', 1='×';
 *                                                        canonical: sorted by (idx, side, kind))
 *     ...  remaining bytes = unknown extension: PRESERVED verbatim by readers, re-emitted
 *          by encode (decode∘encode lossless across versions)
 * ```
 */
object TangoCodec {

    const val VERSION: Int = 1
    private const val MIN_N = TangoPuzzle.MIN_N
    private const val MAX_N = TangoPuzzle.MAX_N

    fun encode(puzzle: TangoPuzzle): ByteArray {
        val variantKey = puzzle.variant.value.toByteArray(Charsets.UTF_8)
        require(variantKey.size in 1..0xFF) { "variant key length out of u8 range" }

        val out = ByteArrayOutputStream(puzzle.cellCount + variantKey.size + 16)
        out.write(VERSION)
        out.write(variantKey.size)
        out.write(variantKey)
        out.write(puzzle.n)

        val givens = puzzle.givens.entries.sortedBy { it.key }
        require(puzzle.givens.size <= 0xFF) { "given count out of u8 range" }
        out.write(puzzle.givens.size)
        givens.forEach { (idx, cell) ->
            out.write(idx)
            out.write(cell.toByte())
        }

        val markers = puzzle.markers.sortedWith(compareBy({ it.cell }, { it.side.ordinal }, { it.kind.ordinal }))
        require(puzzle.markers.size <= 0xFF) { "marker count out of u8 range" }
        out.write(puzzle.markers.size)
        markers.forEach { marker ->
            out.write(marker.cell)
            out.write(marker.side.toByte())
            out.write(marker.kind.toByte())
        }
        // A19: the tail a newer reader added travels verbatim so decode∘encode is lossless.
        out.write(puzzle.unknownTail)
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray): DecodeResult<TangoPuzzle> {
        val cursor = Cursor(bytes)
        val version = cursor.u8() ?: return malformed("empty puzzle blob")
        if (version != VERSION) {
            return DecodeResult.Err(DecodeError.UnsupportedVersion(found = version, maxKnown = VERSION))
        }

        val keyLen = cursor.u8() ?: return malformed("truncated variant key length")
        val variantKey = cursor.bytes(keyLen)?.toString(Charsets.UTF_8)
            ?: return malformed("truncated variant key")
        if (variantKey.isEmpty()) return malformed("empty variant key")
        val variant = com.ivandsky.puzzlebox.game.VariantId(variantKey)

        val n = cursor.u8() ?: return malformed("truncated board size")
        if (n !in MIN_N..MAX_N) return malformed("board size $n out of range [$MIN_N..$MAX_N]")
        // I9: the variant pins the size — parse the pinned n, never guess a rules regime.
        val pinnedN = variantKey.substringAfterLast("/n", "").toIntOrNull()
        if (pinnedN == null) return malformed("variant '$variantKey' is not a tango/n<size> pin")
        if (pinnedN != n) {
            return DecodeResult.Err(DecodeError.VariantSizeMismatch(variantKey, expected = pinnedN, actual = n))
        }

        val givenCount = cursor.u8() ?: return malformed("truncated given count")
        val givens = LinkedHashMap<Int, TangoCell>(givenCount)
        repeat(givenCount) {
            val idx = cursor.u8() ?: return malformed("truncated given index")
            if (idx >= n * n) return malformed("given index $idx out of range")
            val value = cursor.u8()?.let(TangoCell::fromByte) ?: return malformed("truncated given value")
            if (idx in givens) return malformed("duplicate given at $idx")
            givens[idx] = value
        }

        val markerCount = cursor.u8() ?: return malformed("truncated marker count")
        val markers = ArrayList<TangoMarker>(markerCount)
        repeat(markerCount) {
            val cell = cursor.u8() ?: return malformed("truncated marker cell")
            val side = cursor.u8()?.let(TangoEdgeSide::fromByte) ?: return malformed("truncated marker side")
            val kind = cursor.u8()?.let(TangoMarkerKind::fromByte) ?: return malformed("truncated marker kind")
            if (cell >= n * n) return malformed("marker cell $cell out of range")
            if (side == TangoEdgeSide.RIGHT && cell % n == n - 1) return malformed("marker RIGHT on last column")
            if (side == TangoEdgeSide.DOWN && cell / n == n - 1) return malformed("marker DOWN on last row")
            markers += TangoMarker(cell, side, kind)
        }

        // A19: unknown trailing bytes belong to a newer schema version — carry, never reject.
        val unknownTail = cursor.remaining()
        return DecodeResult.Ok(
            TangoPuzzle(variant = variant, n = n, givens = givens, markers = markers, unknownTail = unknownTail),
        )
    }

    private fun malformed(detail: String): DecodeResult.Err =
        DecodeResult.Err(DecodeError.Malformed(detail))

    private class Cursor(private val bytes: ByteArray) {
        var pos = 0

        fun u8(): Int? = bytes.getOrNull(pos++)?.toInt()?.and(0xFF)

        fun bytes(len: Int): ByteArray? {
            if (len < 0 || pos + len > bytes.size) return null
            return bytes.copyOfRange(pos, pos + len).also { pos += len }
        }

        fun remaining(): ByteArray = bytes.copyOfRange(pos, bytes.size)
    }
}
