package com.ivandsky.puzzlebox.games.queens.codec

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensPuzzle
import com.ivandsky.puzzlebox.games.queens.QueensVariants
import com.ivandsky.puzzlebox.games.queens.queensVariantId
import java.io.ByteArrayOutputStream

/**
 * Queens puzzle codec v1 (zip ZipCodec shape; F2 §4 / A19 / ADR-0005). Little-endian;
 * u8 unless noted:
 *
 * ```
 * 0   u8   formatVersion = 1
 * 1   u8   variantKeyLen; UTF-8 bytes        ("queens/n8" stored, never derived from n)
 *     u8   n                                 (2..15 structural; variant must match, I9)
 *     u8   queensPerUnit                     (A9: >=1; n*queensPerUnit solution slots <= 255)
 *     n^2  u8 region id per cell             (row-major, each < n)
 *     u8   flags                             (bit0 = solution present)
 *     [bit0] u8 queenCount; queenCount x u8 cellIdx
 *     ...  remaining bytes = unknown extension: PRESERVED verbatim by readers,
 *          re-emitted by encode (decode∘encode lossless across versions)
 * ```
 *
 * Reader rules (§4): first byte dispatches on exact version (newer -> UnsupportedVersion,
 * dropped, never reinterpreted); unknown trailing bytes ride [QueensPuzzle]'s tail — the
 * codec re-emits them verbatim; encoding is CANONICAL (solution sorted ascending) for
 * archive dedup and editor diff.
 */
object QueensCodec {

    const val VERSION: Int = 1
    private const val MIN_N = 2
    // u8 budget: cells = n^2 must stay <= 255 (n = 15 -> 225); queens = n*queensPerUnit.
    private const val MAX_N = 15

    fun decode(bytes: ByteArray): DecodeResult<QueensPuzzle> {
        val cursor = Cursor(bytes)
        val version = cursor.u8() ?: return errMalformed("empty blob")
        if (version != VERSION) {
            return DecodeResult.Err(DecodeError.UnsupportedVersion(found = version, maxKnown = VERSION))
        }

        val keyLen = (cursor.u8() ?: return errMalformed("truncated variant key length")).toInt()
        val variantKey = cursor.utf8(keyLen) ?: return errMalformed("truncated variant key")
        val variant = QueensVariants.byId(VariantId(variantKey))
            ?: return DecodeResult.Err(DecodeError.UnknownVariant(variantKey))

        val n = (cursor.u8() ?: return errMalformed("truncated n")).toInt()
        if (n !in MIN_N..MAX_N) return errMalformed("n=$n outside $MIN_N..$MAX_N")
        if (variant.size != n) {
            return DecodeResult.Err(
                DecodeError.VariantSizeMismatch(variant = variantKey, expected = variant.size, actual = n),
            )
        }

        val queensPerUnit = (cursor.u8() ?: return errMalformed("truncated queensPerUnit")).toInt()
        if (queensPerUnit < 1) return errMalformed("queensPerUnit $queensPerUnit below 1")
        if (n * queensPerUnit > 255) return errMalformed("n*queensPerUnit exceeds the u8 queen slots")

        if (cursor.remainingCount() < n * n) return errMalformed("truncated region grid")
        val regions = ArrayList<Int>(n * n)
        repeat(n * n) {
            val region = cursor.u8()!!.toInt()
            if (region >= n) return errMalformed("region id $region outside 0 until $n")
            regions += region
        }

        val flags = (cursor.u8() ?: return errMalformed("truncated flags")).toInt()
        var solution: List<Cell>? = null
        if (flags and FLAG_SOLUTION != 0) {
            val queenCount = (cursor.u8() ?: return errMalformed("truncated queen count")).toInt()
            if (queenCount != n * queensPerUnit) {
                return errMalformed("queen count $queenCount != n*queensPerUnit=${n * queensPerUnit}")
            }
            val cells = ArrayList<Cell>(queenCount)
            repeat(queenCount) {
                val cellIdx = (cursor.u8() ?: return errMalformed("truncated queen cell")).toInt()
                if (cellIdx >= n * n) return errMalformed("queen cell $cellIdx out of range for n=$n")
                cells += Cell(cellIdx)
            }
            if (cells.map { it.idx }.toSet().size != queenCount) {
                return errMalformed("duplicate queen cell")
            }
            solution = cells
        }

        return DecodeResult.Ok(
            QueensPuzzle(
                size = n,
                regions = regions,
                queensPerUnit = queensPerUnit,
                solution = solution,
                unknownTail = cursor.remaining(),
            ),
        )
    }

    /** Canonical encoding: one byte sequence per puzzle; foreign variants fail fast. */
    fun encode(puzzle: QueensPuzzle): ByteArray {
        val variant = QueensVariants.byId(queensVariantId(puzzle.size))
            ?: throw IllegalArgumentException("cannot encode unknown variant ${queensVariantId(puzzle.size)}")
        require(variant.size == puzzle.size) { "variant ${variant.id} pins size ${variant.size}" }
        require(variant.queensPerUnit == puzzle.queensPerUnit) {
            "variant ${variant.id} pins queensPerUnit ${variant.queensPerUnit}, puzzle has ${puzzle.queensPerUnit}"
        }

        val keyBytes = variant.id.value.toByteArray(Charsets.UTF_8)
        require(keyBytes.size <= 255) { "variant key too long" }

        val out = ByteArrayOutputStream(puzzle.cellCount + 16 + puzzle.unknownTail.size)
        out.write(VERSION)
        out.write(keyBytes.size)
        out.write(keyBytes)
        out.write(puzzle.size)
        out.write(puzzle.queensPerUnit)
        puzzle.regions.forEach { out.write(it) }
        val solution = puzzle.solution
        if (solution == null) {
            out.write(0)
        } else {
            require(solution.size == puzzle.size * puzzle.queensPerUnit) { "solution must cover the queens" }
            out.write(FLAG_SOLUTION)
            out.write(solution.size)
            solution.map { it.idx }.sorted().forEach { out.write(it) } // canonical order
        }
        out.write(puzzle.unknownTail) // preserved extension bytes, verbatim
        return out.toByteArray()
    }

    private const val FLAG_SOLUTION = 1

    private fun errMalformed(detail: String): DecodeResult.Err =
        DecodeResult.Err(DecodeError.Malformed(detail))

    private class Cursor(private val bytes: ByteArray) {
        var pos: Int = 0

        fun u8(): Int? = bytes.getOrNull(pos++)?.toInt()?.and(0xFF)

        fun utf8(len: Int): String? {
            if (pos + len > bytes.size) return null
            return String(bytes, pos, len, Charsets.UTF_8).also { pos += len }
        }

        fun remainingCount(): Int = bytes.size - pos

        fun remaining(): ByteArray = bytes.copyOfRange(pos, bytes.size).also { pos = bytes.size }
    }
}
