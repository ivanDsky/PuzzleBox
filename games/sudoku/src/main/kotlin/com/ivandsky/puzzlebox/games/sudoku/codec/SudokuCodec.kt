package com.ivandsky.puzzlebox.games.sudoku.codec

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuVariants

/**
 * Puzzle payload v1, the bytes a [com.ivandsky.puzzlebox.game.Puzzle] envelope carries.
 * One byte sequence per puzzle, so archive dedup and replay compare bytes:
 *
 * ```
 * 0   u8   formatVersion = 1
 * 1   u8   variantKeyLen; UTF-8 key      ("sudoku/6x6"; stored, never derived from size)
 *     u8   size                          (must equal the variant's size)
 *     N²   u8 given digit per cell       (0 = open, row-major)
 *     N²   u8 solution digit per cell    (1..N, row-major)
 * ```
 *
 * Decode never throws: a newer version, an unknown variant, a size mismatch, a digit out of
 * range, truncation and trailing bytes all come back as [DecodeResult.Err].
 */
object SudokuCodec {

    const val VERSION: Int = 1

    fun encode(p: SudokuPuzzle): ByteArray {
        val key = p.variant.id.value.toByteArray(Charsets.UTF_8)
        require(key.size <= 0xFF) { "variant key too long" }
        val cells = p.geometry.cellCount
        val body = 3 + key.size
        return ByteArray(body + 2 * cells).also { out ->
            out[0] = VERSION.toByte()
            out[1] = key.size.toByte()
            key.copyInto(out, destinationOffset = 2)
            out[2 + key.size] = p.geometry.size.toByte()
            for (idx in 0 until cells) {
                out[body + idx] = p.givens[idx].toByte()
                out[body + cells + idx] = p.solution[idx].toByte()
            }
        }
    }

    fun decode(bytes: ByteArray): DecodeResult<SudokuPuzzle> {
        if (bytes.isEmpty()) return malformed("empty puzzle blob")
        val version = bytes.u8(0)
        if (version != VERSION) return DecodeResult.Err(DecodeError.UnsupportedVersion(version, VERSION))
        if (bytes.size < 2) return malformed("truncated variant key length")
        val keyLen = bytes.u8(1)
        if (bytes.size < 3 + keyLen) return malformed("truncated variant key or size")
        val key = bytes.decodeToString(2, 2 + keyLen)
        val variant = SudokuVariants.byId(VariantId(key))
            ?: return DecodeResult.Err(DecodeError.UnknownVariant(key))
        val size = bytes.u8(2 + keyLen)
        if (size != variant.geometry.size) {
            return DecodeResult.Err(DecodeError.VariantSizeMismatch(key, variant.geometry.size, size))
        }
        val cells = variant.geometry.cellCount
        val body = 3 + keyLen
        if (bytes.size != body + 2 * cells) return malformed("expected ${2 * cells} cell bytes, got ${bytes.size - body}")
        val givens = List(cells) { bytes.u8(body + it) }
        val solution = List(cells) { bytes.u8(body + cells + it) }
        if (givens.any { it !in 0..size }) return malformed("given digit outside 0..$size")
        if (solution.any { it !in 1..size }) return malformed("solution digit outside 1..$size")
        return DecodeResult.Ok(SudokuPuzzle(variant, givens, solution))
    }

    private fun ByteArray.u8(at: Int): Int = this[at].toInt() and 0xFF

    private fun malformed(detail: String): DecodeResult.Err = DecodeResult.Err(DecodeError.Malformed(detail))
}
