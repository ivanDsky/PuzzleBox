package com.ivandsky.puzzlebox.games.queens.editor

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.editor.EditorShareCodec
import com.ivandsky.puzzlebox.games.queens.QueensVariants
import com.ivandsky.puzzlebox.games.queens.codec.SessionCursor
import com.ivandsky.puzzlebox.games.queens.queensVariantId

/**
 * Versioned board-only Queens share bytes (ADR-0005, ADR-0020):
 *
 * ```
 * v1: [u8 version][u8 size][size² × u8 region]   (row-major, each region in 0 until size)
 * ```
 *
 * No history, no answer crowns and never a solution: the recipient re-solves the board. Only
 * fully painted boards are shareable, so an unpainted cell is written as [UNPAINTED_BYTE] for
 * the encoder to stay total and is rejected as malformed on decode.
 */
internal object QueensShareCodec : EditorShareCodec<QueensDraft> {
    override val shareFormatVersion: Int = 1

    private const val UNPAINTED_BYTE = 0xFF

    override fun encodeShare(draft: QueensDraft): ByteArray {
        val out = ByteArray(2 + draft.regions.size)
        out[0] = shareFormatVersion.toByte()
        out[1] = draft.size.toByte()
        draft.regions.forEachIndexed { idx, region ->
            out[2 + idx] = (if (region == QueensDraft.UNPAINTED) UNPAINTED_BYTE else region).toByte()
        }
        return out
    }

    /** version byte > 1 ⇒ DecodeError.UnsupportedVersion(found, 1); unpainted cell or short payload ⇒ Malformed. */
    override fun decodeShare(bytes: ByteArray): DecodeResult<QueensDraft> {
        val cursor = SessionCursor(bytes)
        val version = cursor.u8() ?: return malformed("empty share bytes")
        if (version > shareFormatVersion) {
            return DecodeResult.Err(DecodeError.UnsupportedVersion(found = version, maxKnown = shareFormatVersion))
        }
        if (version != shareFormatVersion) return malformed("share version $version was never written")
        val size = cursor.u8() ?: return malformed("truncated board size")
        val variant = queensVariantId(size)
        if (QueensVariants.byId(variant) == null) return DecodeResult.Err(DecodeError.UnknownVariant(variant.value))
        val cells = size * size
        val regions = ArrayList<Int>(cells)
        for (idx in 0 until cells) {
            val raw = cursor.u8() ?: return malformed("truncated board: $idx of $cells cells")
            if (raw == UNPAINTED_BYTE) return malformed("cell $idx unpainted")
            if (raw >= size) return malformed("cell $idx region $raw outside 0 until $size")
            regions.add(raw)
        }
        if (cursor.pos != bytes.size) return malformed("${bytes.size - cursor.pos} trailing bytes")
        return DecodeResult.Ok(QueensDraft(size, regions))
    }

    private fun malformed(detail: String): DecodeResult.Err = DecodeResult.Err(DecodeError.Malformed(detail))
}
