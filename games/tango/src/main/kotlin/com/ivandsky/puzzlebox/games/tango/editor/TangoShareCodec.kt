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
 * Versioned givens-and-markers Tango share bytes (ADR-0005, ADR-0020) on the fixed
 * [TangoDraft.N] × [TangoDraft.N] board, cells row-major:
 *
 * ```
 * v1: [u8 version] [u8 g][g × (u8 cell, u8 TangoCell byte)]
 *     [u8 k][k × (u8 cell, u8 side, u8 kind)]
 *     givens ascending by cell; markers sorted by (cell, side, kind)
 * ```
 *
 * Never a solution or history: the recipient re-solves the board. Unordered or repeated givens,
 * off-board cells or edges and trailing bytes are malformed.
 */
internal object TangoShareCodec : EditorShareCodec<TangoDraft> {
    override val shareFormatVersion: Int = 1

    private const val CELLS = TangoDraft.N * TangoDraft.N

    override fun encodeShare(draft: TangoDraft): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(shareFormatVersion)
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

    /** Version byte > 1 is UnsupportedVersion(found, 1); short, unordered or off-board is Malformed. */
    override fun decodeShare(bytes: ByteArray): DecodeResult<TangoDraft> {
        var pos = 0
        fun u8(): Int? = if (pos < bytes.size) bytes[pos++].toInt() and 0xFF else null

        val version = u8() ?: return malformed("empty share bytes")
        if (version > shareFormatVersion) {
            return DecodeResult.Err(DecodeError.UnsupportedVersion(found = version, maxKnown = shareFormatVersion))
        }
        if (version != shareFormatVersion) return malformed("share version $version was never written")

        val givens = LinkedHashMap<Int, TangoCell>()
        repeat(u8() ?: return malformed("truncated given count")) {
            val cell = u8() ?: return malformed("truncated given")
            if (cell >= CELLS || givens.keys.any { it >= cell }) return malformed("given cell $cell unordered or off the board")
            givens[cell] = TangoCell.fromByte(u8() ?: return malformed("truncated given")) ?: return malformed("bad given value")
        }
        val markers = ArrayList<TangoMarker>()
        repeat(u8() ?: return malformed("truncated marker count")) {
            val cell = u8() ?: return malformed("truncated marker")
            val side = TangoEdgeSide.fromByte(u8() ?: return malformed("truncated marker")) ?: return malformed("bad marker side")
            val kind = TangoMarkerKind.fromByte(u8() ?: return malformed("truncated marker")) ?: return malformed("bad marker kind")
            if (!onBoard(cell, side)) return malformed("marker at $cell $side off the board")
            markers.add(TangoMarker(cell, side, kind))
        }
        if (pos != bytes.size) return malformed("${bytes.size - pos} trailing bytes")
        return DecodeResult.Ok(TangoDraft(givens, markers))
    }

    /** The marker's neighbor exists: RIGHT needs a column to the right, DOWN a row below. */
    private fun onBoard(cell: Int, side: TangoEdgeSide): Boolean = cell < CELLS && when (side) {
        TangoEdgeSide.RIGHT -> cell % TangoDraft.N < TangoDraft.N - 1
        TangoEdgeSide.DOWN -> cell / TangoDraft.N < TangoDraft.N - 1
    }

    private fun malformed(detail: String): DecodeResult.Err = DecodeResult.Err(DecodeError.Malformed(detail))
}
