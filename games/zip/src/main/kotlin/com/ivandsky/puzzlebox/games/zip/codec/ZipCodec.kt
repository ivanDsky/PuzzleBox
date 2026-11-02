package com.ivandsky.puzzlebox.games.zip.codec

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.Wall
import com.ivandsky.puzzlebox.games.zip.WallDirection
import com.ivandsky.puzzlebox.games.zip.ZipBoard
import com.ivandsky.puzzlebox.games.zip.ZipPuzzle
import com.ivandsky.puzzlebox.games.zip.engine.ZipVariants

import java.io.ByteArrayOutputStream

/**
 * Zip puzzle codec v1 (F2 §4 / A19 / ADR-0005). Little-endian; u8 unless noted:
 *
 * ```
 * 0   u8   formatVersion = 1
 * 1   u8   variantKeyLen; UTF-8 bytes        ("zip/n6" stored, never derived from n)
 *     u8   n                                 (2..11 structural; variant must match, I9)
 *     u8   wallCount; wallCount x (u8 cellIdx, u8 dir)   (dir: 0=RIGHT, 1=DOWN)
 *     u8   k (waypointCount); k x u8 cellIdx             (ascending labels 1..k)
 *     u8   flags                          (bit0 = solution present)
 *     [bit0] n^2 x u8 cells (solution path in order)
 *     ...  remaining bytes = unknown extension: PRESERVED verbatim by readers,
 *          re-emitted by encode (decode∘encode lossless across versions)
 * ```
 *
 * Reader rules: first byte dispatches on exact version (newer -> UnsupportedVersion,
 * dropped, never reinterpreted); unknown trailing bytes ride [ZipPuzzle.unknownTail];
 * known fields never change meaning; encoding is CANONICAL — exactly one byte sequence
 * per puzzle (walls sorted, deterministic order) for archive dedup and editor diff.
 */
object ZipCodec {

    const val VERSION: Int = 1
    private const val MIN_N = 2
    // u8 field budget: every count/counted index must stay <= 255 — walls <= 2n(n-1)
    // (12x12 already needs 264 wall slots), cells = n^2 (121 at n=11). n = 11 is the
    // largest board the v1 layout can carry; larger n would silently truncate a count.
    private const val MAX_N = 11

    fun decode(bytes: ByteArray): DecodeResult<ZipPuzzle> {
        val cursor = Cursor(bytes)
        val version = cursor.u8() ?: return ErrMalformed("empty blob")
        if (version != VERSION) {
            return DecodeResult.Err(DecodeError.UnsupportedVersion(found = version, maxKnown = VERSION))
        }

        val keyLen = (cursor.u8() ?: return ErrMalformed("truncated variant key length")).toInt()
        val variantKey = cursor.utf8(keyLen) ?: return ErrMalformed("truncated variant key")
        val variant = ZipVariants.byId(VariantId(variantKey))
            ?: return DecodeResult.Err(DecodeError.UnknownVariant(variantKey))

        val n = (cursor.u8() ?: return ErrMalformed("truncated n")).toInt()
        if (n !in MIN_N..MAX_N) return ErrMalformed("n=$n outside $MIN_N..$MAX_N")
        if (variant.size != n) {
            return DecodeResult.Err(
                DecodeError.VariantSizeMismatch(variant = variantKey, expected = variant.size, actual = n),
            )
        }

        val wallCount = (cursor.u8() ?: return ErrMalformed("truncated wall count")).toInt()
        val walls = HashSet<Wall>(wallCount)
        repeat(wallCount) {
            val cellIdx = (cursor.u8() ?: return ErrMalformed("truncated wall cell")).toInt()
            val dir = (cursor.u8() ?: return ErrMalformed("truncated wall direction")).toInt()
            if (cellIdx >= n * n) return ErrMalformed("wall cell $cellIdx out of range for n=$n")
            val direction = when (dir) {
                0 -> WallDirection.RIGHT
                1 -> WallDirection.DOWN
                else -> return ErrMalformed("wall direction $dir unknown")
            }
            walls += Wall(Cell(cellIdx), direction)
        }

        val waypointCount = (cursor.u8() ?: return ErrMalformed("truncated waypoint count")).toInt()
        if (waypointCount < 1) return ErrMalformed("need at least the start waypoint")
        val waypoints = ArrayList<Cell>(waypointCount)
        val seen = HashSet<Int>(waypointCount)
        repeat(waypointCount) {
            val cellIdx = (cursor.u8() ?: return ErrMalformed("truncated waypoint cell")).toInt()
            if (cellIdx >= n * n) return ErrMalformed("waypoint cell $cellIdx out of range for n=$n")
            if (!seen.add(cellIdx)) return ErrMalformed("duplicate waypoint cell $cellIdx")
            waypoints += Cell(cellIdx)
        }

        val flags = (cursor.u8() ?: return ErrMalformed("truncated flags")).toInt()
        var solution: List<Cell>? = null
        if (flags and FLAG_SOLUTION != 0) {
            val cells = ArrayList<Cell>(n * n)
            repeat(n * n) {
                val cellIdx = (cursor.u8() ?: return ErrMalformed("truncated solution cell")).toInt()
                if (cellIdx >= n * n) return ErrMalformed("solution cell $cellIdx out of range for n=$n")
                cells += Cell(cellIdx)
            }
            if (cells.map { it.idx }.distinct().size != n * n) {
                return ErrMalformed("solution is not a permutation of the board")
            }
            solution = cells
        }

        return DecodeResult.Ok(
            ZipPuzzle(
                variant = variant.id,
                board = ZipBoard(n = n, walls = walls, waypoints = waypoints),
                solution = solution,
                unknownTail = cursor.remaining(),
            ),
        )
    }

    /** Canonical encoding: one byte sequence per puzzle; foreign variants fail fast. */
    fun encode(puzzle: ZipPuzzle): ByteArray {
        val variant = ZipVariants.byId(puzzle.variant)
            ?: throw IllegalArgumentException("cannot encode unknown variant ${puzzle.variant}")
        val n = puzzle.board.n
        require(n == variant.size) { "variant ${variant.id} pins size ${variant.size}, board has $n" }

        val keyBytes = puzzle.variant.value.toByteArray(Charsets.UTF_8)
        require(keyBytes.size <= 255) { "variant key too long" }
        require(puzzle.board.walls.size <= 255) { "v1 layout carries u8 wall counts" }
        require(puzzle.board.waypoints.size in 1..255) { "v1 layout carries u8 waypoint counts" }

        val out = ByteArrayOutputStream(64 + puzzle.unknownTail.size)
        out.write(VERSION)
        out.write(keyBytes.size)
        out.write(keyBytes)
        out.write(n)
        out.write(puzzle.board.walls.size)
        puzzle.board.walls
            .sortedWith(compareBy({ it.cell.idx }, { it.direction.ordinal })) // canonical order
            .forEach { wall ->
                out.write(wall.cell.idx)
                out.write(
                    when (wall.direction) {
                        WallDirection.RIGHT -> 0
                        WallDirection.DOWN -> 1
                    },
                )
            }
        out.write(puzzle.board.waypoints.size)
        puzzle.board.waypoints.forEach { out.write(it.idx) }
        val solution = puzzle.solution
        if (solution == null) {
            out.write(0)
        } else {
            require(solution.size == n * n) { "solution must cover the board" }
            out.write(FLAG_SOLUTION)
            solution.forEach { out.write(it.idx) }
        }
        out.write(puzzle.unknownTail) // preserved extension bytes, verbatim
        return out.toByteArray()
    }

    private const val FLAG_SOLUTION = 1

    private fun ErrMalformed(detail: String): DecodeResult.Err =
        DecodeResult.Err(DecodeError.Malformed(detail))

    private class Cursor(private val bytes: ByteArray) {
        var pos: Int = 0

        fun u8(): Int? = bytes.getOrNull(pos++)?.toInt()?.and(0xFF)

        fun utf8(len: Int): String? {
            if (pos + len > bytes.size) return null
            return String(bytes, pos, len, Charsets.UTF_8).also { pos += len }
        }

        fun remaining(): ByteArray = bytes.copyOfRange(pos, bytes.size).also { pos = bytes.size }
    }
}
