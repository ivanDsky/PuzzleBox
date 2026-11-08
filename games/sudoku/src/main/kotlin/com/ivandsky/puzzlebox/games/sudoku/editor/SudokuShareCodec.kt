package com.ivandsky.puzzlebox.games.sudoku.editor

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.editor.EditorShareCodec

/**
 * Versioned givens-only Sudoku share bytes (ADR-0005, ADR-0020):
 *
 * ```
 * v1: [u8 version][u8 size][size² × u8 digit]   (row-major, digit 0..size, 0 = empty)
 * ```
 *
 * No history and never a solution: the recipient re-solves the board.
 */
internal object SudokuShareCodec : EditorShareCodec<SudokuDraft> {
    override val shareFormatVersion: Int = 1

    override fun encodeShare(draft: SudokuDraft): ByteArray {
        val out = ByteArray(2 + draft.givens.size)
        out[0] = shareFormatVersion.toByte()
        out[1] = draft.size.toByte()
        draft.givens.forEachIndexed { idx, digit -> out[2 + idx] = digit.toByte() }
        return out
    }

    /**
     * version byte > 1 ⇒ UnsupportedVersion(found, 1); a short, long or out-of-range payload ⇒
     * Malformed.
     */
    override fun decodeShare(bytes: ByteArray): DecodeResult<SudokuDraft> {
        if (bytes.isEmpty()) return malformed("empty share bytes")
        val version = bytes.u8(0)
        if (version > shareFormatVersion) {
            return DecodeResult.Err(DecodeError.UnsupportedVersion(found = version, maxKnown = shareFormatVersion))
        }
        if (version != shareFormatVersion) return malformed("share version $version was never written")
        if (bytes.size < 2) return malformed("truncated board size")
        val size = bytes.u8(1)
        if (size !in SudokuDraft.SIZES) return DecodeResult.Err(DecodeError.UnknownVariant("sudoku/${size}x$size"))
        val cells = size * size
        if (bytes.size != 2 + cells) return malformed("expected $cells cell bytes, got ${bytes.size - 2}")
        val givens = List(cells) { bytes.u8(2 + it) }
        val bad = givens.indexOfFirst { it > size }
        if (bad >= 0) return malformed("cell $bad digit ${givens[bad]} outside 0..$size")
        return DecodeResult.Ok(SudokuDraft(size, givens))
    }

    private fun ByteArray.u8(at: Int): Int = this[at].toInt() and 0xFF

    private fun malformed(detail: String): DecodeResult.Err = DecodeResult.Err(DecodeError.Malformed(detail))
}
