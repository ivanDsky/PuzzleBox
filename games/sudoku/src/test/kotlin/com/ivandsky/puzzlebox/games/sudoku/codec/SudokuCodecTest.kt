package com.ivandsky.puzzlebox.games.sudoku.codec

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.games.sudoku.KnownSolutions
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SudokuCodecTest {

    private val puzzles = listOf(KnownSolutions.peanut450, KnownSolutions.miniSample, KnownSolutions.classicSample)

    @Test
    fun `puzzles round trip and re-encode to the same bytes`() {
        for (puzzle in puzzles) {
            val bytes = SudokuCodec.encode(puzzle)
            val decoded = (SudokuCodec.decode(bytes) as DecodeResult.Ok).value
            assertEquals(puzzle, decoded)
            assertArrayEquals(bytes, SudokuCodec.encode(decoded))
        }
    }

    @Test
    fun `the layout is version, variant key, size, givens, solution`() {
        val bytes = SudokuCodec.encode(KnownSolutions.peanut450)
        assertEquals(1, bytes[0].toInt())
        assertEquals("sudoku/6x6", bytes.decodeToString(2, 2 + bytes[1]))
        assertEquals(6, bytes[12].toInt())
        assertEquals(13 + 72, bytes.size)
    }

    @Test
    fun `every truncated prefix and any trailing byte is an error, never a throw`() {
        for (puzzle in puzzles) {
            val bytes = SudokuCodec.encode(puzzle)
            for (len in 0 until bytes.size) {
                assertTrue(SudokuCodec.decode(bytes.copyOf(len)) is DecodeResult.Err)
            }
            assertTrue(SudokuCodec.decode(bytes + 0) is DecodeResult.Err)
        }
    }

    @Test
    fun `a newer version is reported as unsupported`() {
        val bytes = SudokuCodec.encode(KnownSolutions.peanut450).also { it[0] = 2 }
        assertEquals(DecodeResult.Err(DecodeError.UnsupportedVersion(2, 1)), SudokuCodec.decode(bytes))
    }

    @Test
    fun `an unknown variant and a size mismatch are typed errors`() {
        val unknown = SudokuCodec.encode(KnownSolutions.peanut450).also { it[9] = '8'.code.toByte() }
        assertEquals(DecodeResult.Err(DecodeError.UnknownVariant("sudoku/8x6")), SudokuCodec.decode(unknown))
        val resized = SudokuCodec.encode(KnownSolutions.peanut450).also { it[12] = 9 }
        assertEquals(DecodeResult.Err(DecodeError.VariantSizeMismatch("sudoku/6x6", 6, 9)), SudokuCodec.decode(resized))
    }

    @Test
    fun `digits outside the board are malformed`() {
        val bigGiven = SudokuCodec.encode(KnownSolutions.peanut450).also { it[13] = 7 }
        assertTrue((SudokuCodec.decode(bigGiven) as DecodeResult.Err).error is DecodeError.Malformed)
        val zeroSolution = SudokuCodec.encode(KnownSolutions.peanut450).also { it[13 + 36] = 0 }
        assertTrue((SudokuCodec.decode(zeroSolution) as DecodeResult.Err).error is DecodeError.Malformed)
    }
}
