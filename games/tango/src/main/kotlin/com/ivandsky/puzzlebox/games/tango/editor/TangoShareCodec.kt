package com.ivandsky.puzzlebox.games.tango.editor

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.editor.EditorShareCodec
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarker
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import java.io.ByteArrayOutputStream

/**
 * Versioned givens-and-markers Tango share bytes (ADR-0005, ADR-0020), cells row-major on the
 * n × n board:
 *
 * ```
 * v1: [u8 1]           body      (always 6×6; still written for every 6×6 board)
 * v2: [u8 2] [u8 size] body      (size ∈ TangoDraft.SIZES, never 6)
 * body = [u8 g][g × (u8 cell, u8 TangoCell byte)] [u8 k][k × (u8 cell, u8 side, u8 kind)]
 *        givens ascending by cell; markers strictly ascending by (cell, side), one per edge
 * ```
 *
 * A 6×6 board stays v1 so builds that read only v1 keep opening it; any other size is v2, which
 * those builds turn into NeedsUpdate (doc version 2 > 1) instead of misreading it as 6×6.
 * Never a solution or history: the recipient re-solves the board. Unordered or repeated givens
 * or markers, off-board cells or edges, a v2 6×6 and trailing bytes are malformed; a v2 size
 * outside SIZES is an unknown variant.
 */
internal object TangoShareCodec : EditorShareCodec<TangoDraft> {
    override val shareFormatVersion: Int = 2

    /** v1 bytes are always 6×6. */
    private const val V1_N = 6

    override fun shareFormatVersionOf(draft: TangoDraft): Int = if (draft.n == V1_N) 1 else 2

    override fun encodeShare(draft: TangoDraft): ByteArray {
        val out = ByteArrayOutputStream()
        val version = shareFormatVersionOf(draft)
        out.write(version)
        if (version == 2) out.write(draft.n)
        out.write(draft.givens.size)
        draft.givens.toSortedMap().forEach { (cell, value) ->
            out.write(cell)
            out.write(value.toByte())
        }
        out.write(draft.markers.size)
        draft.markers.sortedWith(compareBy({ it.cell }, { it.side.ordinal }, { it.kind.ordinal })).forEach {
            out.write(it.cell)
            out.write(it.side.toByte())
            out.write(it.kind.toByte())
        }
        return out.toByteArray()
    }

    /**
     * Version > 2 is UnsupportedVersion(found, 2); a v2 size outside SIZES is UnknownVariant;
     * short, unordered or off-board is Malformed.
     */
    override fun decodeShare(bytes: ByteArray): DecodeResult<TangoDraft> {
        var pos = 0
        fun u8(): Int? = if (pos < bytes.size) bytes[pos++].toInt() and 0xFF else null

        val version = u8() ?: return malformed("empty share bytes")
        if (version > shareFormatVersion) {
            return DecodeResult.Err(DecodeError.UnsupportedVersion(found = version, maxKnown = shareFormatVersion))
        }
        val n = when (version) {
            1 -> V1_N
            2 -> {
                val size = u8() ?: return malformed("truncated board size")
                if (size !in TangoDraft.SIZES) return DecodeResult.Err(DecodeError.UnknownVariant("tango/n$size"))
                if (size == V1_N) return malformed("a $V1_N×$V1_N board is written as v1")
                size
            }
            else -> return malformed("share version $version was never written")
        }
        val cells = n * n

        val givens = LinkedHashMap<Int, TangoCell>()
        repeat(u8() ?: return malformed("truncated given count")) {
            val cell = u8() ?: return malformed("truncated given")
            if (cell >= cells || givens.keys.any { it >= cell }) return malformed("given cell $cell unordered or off the board")
            givens[cell] = TangoCell.fromByte(u8() ?: return malformed("truncated given")) ?: return malformed("bad given value")
        }
        val markers = ArrayList<TangoMarker>()
        var lastEdge = -1
        repeat(u8() ?: return malformed("truncated marker count")) {
            val cell = u8() ?: return malformed("truncated marker")
            val side = TangoEdgeSide.fromByte(u8() ?: return malformed("truncated marker")) ?: return malformed("bad marker side")
            val kind = TangoMarkerKind.fromByte(u8() ?: return malformed("truncated marker")) ?: return malformed("bad marker kind")
            if (!onBoard(n, cell, side)) return malformed("marker at $cell $side off the board")
            val edge = cell * 2 + side.ordinal
            if (edge <= lastEdge) return malformed("marker at $cell $side unordered or repeated")
            lastEdge = edge
            markers.add(TangoMarker(cell, side, kind))
        }
        if (pos != bytes.size) return malformed("${bytes.size - pos} trailing bytes")
        return DecodeResult.Ok(TangoDraft(n, givens, markers))
    }

    /** The marker's neighbor exists: RIGHT needs a column to the right, DOWN a row below. */
    private fun onBoard(n: Int, cell: Int, side: TangoEdgeSide): Boolean = cell < n * n && when (side) {
        TangoEdgeSide.RIGHT -> cell % n < n - 1
        TangoEdgeSide.DOWN -> cell / n < n - 1
    }

    private fun malformed(detail: String): DecodeResult.Err = DecodeResult.Err(DecodeError.Malformed(detail))
}
