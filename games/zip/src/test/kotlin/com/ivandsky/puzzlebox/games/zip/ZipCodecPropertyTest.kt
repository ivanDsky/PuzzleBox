package com.ivandsky.puzzlebox.games.zip

import com.ivandsky.puzzlebox.games.zip.codec.ZipCodec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Codec v1 contract (F2 §4): exact round trip (C1), hand-pinned canonical bytes (C2),
 * canonical determinism (C3), unknown-extension preservation (C4), version guard (C5),
 * and the malformed-input flood.
 */
class ZipCodecPropertyTest {

    private fun canonicalPuzzle(tail: ByteArray = ByteArray(0)): ZipPuzzle =
        ZipPuzzle(
            variant = ZipTestSupport.variantOf(5).id,
            board = ZipBoard(
                n = 5,
                walls = setOf(
                    Wall(Cell(0), WallDirection.DOWN),
                    Wall(Cell(0), WallDirection.RIGHT),
                    Wall(Cell(7), WallDirection.DOWN),
                ),
                waypoints = listOf(Cell(0), Cell(12), Cell(24)),
            ),
            solution = ZipTestSupport.n5Solution,
            unknownTail = tail,
        )

    /** C1: decode(encode(p)) == p for the whole object, tail included. */
    @Test
    fun `C1 - decode-encode round trip preserves equality exactly`() {
        val puzzle = canonicalPuzzle(tail = byteArrayOf(0xCA.toByte(), 0xFE.toByte()))
        val bytes = ZipCodec.encode(puzzle)
        assertEquals(puzzle, ZipCodec.decode(bytes).getOrThrow())
        assertArrayEquals(bytes, ZipCodec.encode(ZipCodec.decode(bytes).getOrThrow()))
    }

    @Test
    fun `C1 - generated boards round trip with empty tails`() {
        val generator = ZipTestSupport.seededGenerator(592)
        repeat(5) {
            val puzzle = generator.nextUnique(ZipTestSupport.variantOf(5))!!
            val bytes = ZipCodec.encode(puzzle)
            assertEquals(puzzle, ZipCodec.decode(bytes).getOrThrow())
        }
    }

    /** C2: the §4 layout, pinned byte for byte (u8 fields, wall pairs, flags, solution). */
    @Test
    fun `C2 - byte layout matches the section-4 table exactly`() {
        val bytes = ZipCodec.encode(canonicalPuzzle())
        val expected = buildList {
            add(1) // formatVersion
            add(6) // variantKeyLen
            addAll("zip/n5".toByteArray(Charsets.UTF_8).map(Byte::toInt))
            add(5) // n
            add(3) // wallCount
            add(0); add(0) // wall (0, RIGHT)
            add(0); add(1) // wall (0, DOWN)
            add(7); add(1) // wall (7, DOWN)
            add(3) // waypointCount
            add(0); add(12); add(24)
            add(1) // flags: solution present
            addAll(ZipTestSupport.n5Solution.map(Cell::idx))
        }.map(Int::toByte).toByteArray()
        assertArrayEquals(expected, bytes)
    }

    /** C3: canonical form — wall set iteration order never leaks into the bytes. */
    @Test
    fun `C3 - encoding is canonical across set iteration orders`() {
        val a = canonicalPuzzle()
        val b = a.copy(
            board = a.board.copy(
                walls = a.board.walls.reversed().toSet(), // different iteration order, same set
            ),
        )
        assertArrayEquals(ZipCodec.encode(a), ZipCodec.encode(b))
    }

    /** C4: unknown trailing bytes ride along and are re-emitted verbatim. */
    @Test
    fun `C4 - unknown tail is preserved through decode and re-encode`() {
        val tail = byteArrayOf(9, 9, 9)
        val extended = ZipCodec.encode(canonicalPuzzle()) + tail
        val decoded = ZipCodec.decode(extended).getOrThrow()
        assertArrayEquals(tail, decoded.unknownTail)
        assertArrayEquals(extended, ZipCodec.encode(decoded))
    }

    /** C5: first-byte dispatch on exact version — future versions are dropped, never reinterpreted. */
    @Test
    fun `C5 - unknown format version is UnsupportedVersion`() {
        val newer = byteArrayOf(2) + ZipCodec.encode(canonicalPuzzle()).copyOfRange(1, 20)
        val error = (ZipCodec.decode(newer) as? com.ivandsky.puzzlebox.game.DecodeResult.Err)?.error
        assertTrue(error is com.ivandsky.puzzlebox.game.DecodeError.UnsupportedVersion)
        error as com.ivandsky.puzzlebox.game.DecodeError.UnsupportedVersion
        assertEquals(2, error.found)
        assertEquals(ZipCodec.VERSION, error.maxKnown)
    }

    @Test
    fun `unknown variant key is UnknownVariant`() {
        val bytes = ZipCodec.encode(canonicalPuzzle()).let { mutated ->
            // rewrite the variant key in place: "zip/n5" -> "zip/n4" (same length)
            val keyStart = 2
            "zip/n4".toByteArray(Charsets.UTF_8).forEachIndexed { i, b -> mutated[keyStart + i] = b }
            mutated
        }
        val error = (ZipCodec.decode(bytes) as? com.ivandsky.puzzlebox.game.DecodeResult.Err)?.error
        assertTrue(error is com.ivandsky.puzzlebox.game.DecodeError.UnknownVariant)
    }

    /** I9 at the codec boundary: the variant pins the size; a mismatched n is rejected. */
    @Test
    fun `I9 - structural n contradicting the variant is VariantSizeMismatch`() {
        val bytes = ZipCodec.encode(canonicalPuzzle()).also { it[8] = 6 } // n byte, after version + key
        val error = (ZipCodec.decode(bytes) as? com.ivandsky.puzzlebox.game.DecodeResult.Err)?.error
        assertTrue(error is com.ivandsky.puzzlebox.game.DecodeError.VariantSizeMismatch)
        error as com.ivandsky.puzzlebox.game.DecodeError.VariantSizeMismatch
        assertEquals("zip/n5", error.variant)
        assertEquals(5, error.expected)
        assertEquals(6, error.actual)
    }

    @Test
    fun `truncated and out-of-range blobs are Malformed`() {
        val full = ZipCodec.encode(canonicalPuzzle())
        // every truncation point fails, none crash
        for (cut in 0 until full.size) {
            val result = ZipCodec.decode(full.copyOf(cut))
            assertTrue("cut=$cut", result is com.ivandsky.puzzlebox.game.DecodeResult.Err)
            assertTrue(
                (result as com.ivandsky.puzzlebox.game.DecodeResult.Err).error
                    is com.ivandsky.puzzlebox.game.DecodeError.Malformed,
            )
        }
        // n outside 2..11 is structural garbage (u8 field budget cap)
        val badN = full.copyOf().also { it[8] = 17 }
        assertTrue(ZipCodec.decode(badN) is com.ivandsky.puzzlebox.game.DecodeResult.Err)
        // empty blob
        assertTrue(ZipCodec.decode(ByteArray(0)) is com.ivandsky.puzzlebox.game.DecodeResult.Err)
    }

    @Test
    fun `solution must be a permutation of the board`() {
        val puzzle = canonicalPuzzle()
        // encode allows any n*n cells; decode rejects the non-permutation (trust boundary)
        val duplicated = puzzle.copy(solution = List(puzzle.board.cellCount) { Cell(0) })
        val bytes = ZipCodec.encode(duplicated)
        val result = ZipCodec.decode(bytes)
        assertTrue(result is com.ivandsky.puzzlebox.game.DecodeResult.Err)
        assertTrue(
            (result as com.ivandsky.puzzlebox.game.DecodeResult.Err).error
                is com.ivandsky.puzzlebox.game.DecodeError.Malformed,
        )
        // encode refuses a solution that does not cover the board
        val short = puzzle.copy(solution = puzzle.solution!!.dropLast(1))
        var threw = false
        try {
            ZipCodec.encode(short)
        } catch (expected: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun `foreign variants and bad sizes fail fast on encode`() {
        var threw = false
        try {
            ZipCodec.encode(canonicalPuzzle().copy(variant = com.ivandsky.puzzlebox.game.VariantId("zip/n9")))
        } catch (expected: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }
}
