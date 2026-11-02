package com.ivandsky.puzzlebox.games.queens

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.games.queens.codec.QueensCodec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Codec contract tests: roundtrip, canonical bytes, reader rules, malformed rejection. */
class QueensCodecTest {

    /** Deterministic 4-cell demo puzzle (2x2, two regions) — codec-level, size within bounds. */
    private fun puzzle(size: Int = 8, withSolution: Boolean = true, tail: ByteArray = ByteArray(0)): QueensPuzzle {
        val n = size
        // checkerboard regions: valid ids 0 until n, no structural claim beyond the codec's
        val regions = (0 until n * n).map { (it / n + it % n) % n }
        val solution = if (withSolution) {
            (0 until n).map { Cell(it * n + it) }.let { cells ->
                require(cells.map { it.idx }.toSet().size == n)
                cells
            }
        } else {
            null
        }
        return QueensPuzzle(size = n, regions = regions, solution = solution, unknownTail = tail)
    }

    @Test
    fun `decode-encode round trips byte-identically`() {
        val bytes = QueensCodec.encode(puzzle())
        val decoded = QueensCodec.decode(bytes).getOrThrow()
        assertArrayEquals(bytes, QueensCodec.encode(decoded))
        assertEquals(puzzle(), decoded)
    }

    @Test
    fun `solution-less puzzles round trip`() {
        val bytes = QueensCodec.encode(puzzle(withSolution = false))
        val decoded = QueensCodec.decode(bytes).getOrThrow()
        assertNull(decoded.solution)
        assertArrayEquals(bytes, QueensCodec.encode(decoded))
    }

    @Test
    fun `unknown trailing bytes are preserved verbatim (A19)`() {
        val tail = byteArrayOf(0xAB.toByte(), 0x07, 0x7F)
        val bytes = QueensCodec.encode(puzzle(tail = tail))
        val decoded = QueensCodec.decode(bytes).getOrThrow()
        assertArrayEquals(tail, decoded.unknownTail)
        assertArrayEquals(bytes, QueensCodec.encode(decoded))
    }

    @Test
    fun `encode is canonical - decode of wire-order solution re-encodes sorted`() {
        val n = 8
        val base = puzzle(n)
        val shuffled = base.copy(solution = base.solution!!.reversed())
        assertArrayEquals(QueensCodec.encode(base), QueensCodec.encode(shuffled))
    }

    @Test
    fun `newer version byte is UnsupportedVersion, never reinterpreted`() {
        val bytes = QueensCodec.encode(puzzle()).clone().also { it[0] = 2.toByte() }
        val error = (QueensCodec.decode(bytes) as DecodeResult.Err).error
        assertEquals(DecodeError.UnsupportedVersion(found = 2, maxKnown = 1), error)
    }

    @Test
    fun `unknown variants are never guessed`() {
        val bytes = QueensCodec.encode(puzzle(size = 8)).clone().also { it[2] = 'x'.code.toByte() } // key "xueens/n8"
        assertTrue(QueensCodec.decode(bytes) is DecodeResult.Err)
    }

    @Test
    fun `size-variant mismatch is VariantSizeMismatch`() {
        // layout: [0]=version, [1]=keyLen, [2..10]="queens/n8", [11]=n — rewrite n to 9
        val bytes = QueensCodec.encode(puzzle(8)).clone().also { it[11] = 9.toByte() }
        val error = (QueensCodec.decode(bytes) as DecodeResult.Err).error
        assertEquals(DecodeError.VariantSizeMismatch("queens/n8", expected = 8, actual = 9), error)
    }

    @Test
    fun `malformed blobs are Malformed, never thrown`() {
        fun err(bytes: ByteArray): DecodeError = when (val r = QueensCodec.decode(bytes)) {
            is DecodeResult.Err -> r.error
            is DecodeResult.Ok -> throw AssertionError("expected rejection")
        }
        assertTrue(err(ByteArray(0)) is DecodeError.Malformed) // empty
        assertTrue(err(byteArrayOf(1, 9)) is DecodeError.Malformed) // truncated key length
        assertTrue(err(QueensCodec.encode(puzzle()).copyOfRange(0, 20)) is DecodeError.Malformed) // truncated regions
        // region id out of range: layout [12]=queensPerUnit, [13..]=regions — cell 0 claims 9
        val poisoned = QueensCodec.encode(puzzle(8)).clone().also { it[13] = 9.toByte() }
        assertTrue(err(poisoned) is DecodeError.Malformed)
    }

    @Test
    fun `constructor rejects structurally invalid puzzles`() {
        assertTrue(
            runCatching { QueensPuzzle(size = 3, regions = List(8) { 0 }) }.isFailure,
        )
        assertTrue(
            runCatching { QueensPuzzle(size = 3, regions = List(9) { 5 }) }.isFailure, // region id out of range
        )
        assertTrue(
            runCatching {
                QueensPuzzle(size = 3, regions = List(9) { 0 }, solution = List(2) { Cell(0) })
            }.isFailure, // wrong queen count
        )
    }

    private fun <T> DecodeResult<T>.getOrThrow(): T = when (this) {
        is DecodeResult.Ok -> value
        is DecodeResult.Err -> throw AssertionError("decode failed: $error")
    }
}
