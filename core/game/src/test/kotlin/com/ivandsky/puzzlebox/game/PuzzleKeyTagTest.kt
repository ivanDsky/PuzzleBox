package com.ivandsky.puzzlebox.game

import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PuzzleKeyTagTest {

    private val tagged = 3

    /** A one-byte Daily body (epochDay, then number or 0xFF for none) standing in for a codec's own layout. */
    private fun write(key: PuzzleKey, afterTag: () -> Unit = {}): List<Int> {
        val out = mutableListOf<Int>()
        PuzzleKeyTag.write(key, out::add, afterTag) { daily ->
            out += daily.date.toEpochDay().toInt()
            out += daily.puzzleNumber ?: 0xFF
        }
        return out
    }

    private fun read(bytes: List<Int>, version: Int = tagged, afterTag: () -> Boolean = { true }): DecodeResult<PuzzleKey> {
        val source = bytes.iterator()
        val u8 = { if (source.hasNext()) source.next() else null }
        return PuzzleKeyTag.read(version, taggedSince = tagged, u8 = u8, afterTag = afterTag) {
            val epochDay = u8() ?: return@read malformed
            val number = u8() ?: return@read malformed
            PuzzleKeyTag.daily(epochDay.toLong(), number.takeIf { it != 0xFF }?.toLong())
        }
    }

    private val malformed = DecodeResult.Err(DecodeError.Malformed("truncated daily"))

    private fun assertMalformed(result: DecodeResult<*>) =
        assertTrue("expected Malformed, got $result", (result as? DecodeResult.Err)?.error is DecodeError.Malformed)

    @Test
    fun `a daily is tag 0 then the codec's own body`() {
        val key = PuzzleKey.Daily(LocalDate.ofEpochDay(7), 42)

        assertEquals(listOf(0, 7, 42), write(key))
        assertEquals(DecodeResult.Ok(key), read(write(key)))
    }

    @Test
    fun `a custom is tag 1, a u8 length, then UTF-8`() {
        val key = PuzzleKey.Custom("Zé")

        assertEquals(listOf(1, 3, 'Z'.code, 0xC3, 0xA9), write(key))
        assertEquals(DecodeResult.Ok(key), read(write(key)))
    }

    @Test
    fun `a ref older than the tag is an untagged daily`() {
        assertEquals(DecodeResult.Ok(PuzzleKey.Daily(LocalDate.ofEpochDay(1), null)), read(listOf(1, 0xFF), version = tagged - 1))
    }

    @Test
    fun `a ref at a newer version still reads its tag`() {
        assertEquals(DecodeResult.Ok(PuzzleKey.Custom("A")), read(listOf(1, 1, 'A'.code), version = tagged + 1))
    }

    @Test
    fun `the hook runs between the tag and the body on both sides`() {
        val written = mutableListOf<Int>()
        PuzzleKeyTag.write(PuzzleKey.Custom("A"), written::add, afterTag = { written += 9 }) { }
        assertEquals(listOf(1, 9, 1, 'A'.code), written)

        val source = written.iterator()
        val seen = mutableListOf<Int>()
        val key = PuzzleKeyTag.read(tagged, tagged, u8 = { source.next() }, afterTag = { seen += source.next(); true }) { malformed }
        assertEquals(listOf(9), seen)
        assertEquals(DecodeResult.Ok(PuzzleKey.Custom("A")), key)
    }

    @Test
    fun `a failing hook is malformed`() {
        assertMalformed(read(listOf(1, 1, 'A'.code), afterTag = { false }))
    }

    @Test
    fun `a zero-length custom id is rejected on both sides`() {
        assertThrows(IllegalArgumentException::class.java) { write(PuzzleKey.Custom("")) }
        assertMalformed(read(listOf(1, 0)))
    }

    @Test
    fun `a custom id over 255 UTF-8 bytes cannot be written`() {
        assertThrows(IllegalArgumentException::class.java) { write(PuzzleKey.Custom("é".repeat(128))) }
    }

    @Test
    fun `bytes that are not UTF-8 are malformed, not replaced`() {
        assertMalformed(read(listOf(1, 2, 0xC3, 0x28)))
        assertMalformed(read(listOf(1, 1, 0xFF)))
    }

    @Test
    fun `truncation and unknown tags are malformed`() {
        assertMalformed(read(emptyList()))
        assertMalformed(read(listOf(2)))
        assertMalformed(read(listOf(1)))
        assertMalformed(read(listOf(1, 3, 'A'.code)))
        assertMalformed(read(listOf(0, 1)))
    }

    @Test
    fun `a daily outside LocalDate or a number outside Int is malformed`() {
        assertMalformed(PuzzleKeyTag.daily(LocalDate.MAX.toEpochDay() + 1, null))
        assertMalformed(PuzzleKeyTag.daily(LocalDate.MIN.toEpochDay() - 1, null))
        assertMalformed(PuzzleKeyTag.daily(0, Int.MAX_VALUE + 1L))
        assertMalformed(PuzzleKeyTag.daily(0, Int.MIN_VALUE - 1L))
        assertEquals(
            DecodeResult.Ok(PuzzleKey.Daily(LocalDate.MIN, Int.MIN_VALUE)),
            PuzzleKeyTag.daily(LocalDate.MIN.toEpochDay(), Int.MIN_VALUE.toLong()),
        )
    }
}
