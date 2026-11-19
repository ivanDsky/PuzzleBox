package com.ivandsky.puzzlebox.game.trainer

import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.model.GameId
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TechniqueIndexTest {

    private val lineLock = TechniqueId("line-lock")
    private val singles = TechniqueId("singles")
    private val pigeonhole = TechniqueId("pigeonhole")

    private fun index(entries: Map<Int, Map<TechniqueId, Int>>) =
        TechniqueIndex(GameId("queens"), techniqueSetVersion = 3, cutoff = LocalDate.of(2026, 11, 18), entries = entries)

    private fun malformed(text: String): String {
        val result = TechniqueIndex.read(text)
        assertTrue("expected Err, got $result", result is DecodeResult.Err)
        val error = (result as DecodeResult.Err).error
        assertTrue("expected Malformed, got $error", error is DecodeError.Malformed)
        return (error as DecodeError.Malformed).detail
    }

    @Test
    fun `write then read gives back an equal index`() {
        val original = index(
            linkedMapOf(
                205 to linkedMapOf(singles to 6, pigeonhole to 1),
                101 to linkedMapOf(singles to 4, lineLock to 2),
                150 to emptyMap<TechniqueId, Int>(),
                102 to mapOf(singles to 6),
            ),
        )

        val result = TechniqueIndex.read(TechniqueIndex.write(original))

        assertEquals(DecodeResult.Ok(original), result)
    }

    @Test
    fun `write is byte-stable whatever order the maps hold`() {
        val shuffled = index(
            linkedMapOf(
                102 to mapOf(singles to 6),
                101 to linkedMapOf(singles to 4, lineLock to 2),
            ),
        )
        val sorted = index(
            linkedMapOf(
                101 to linkedMapOf(lineLock to 2, singles to 4),
                102 to mapOf(singles to 6),
            ),
        )

        val expected = "technique-index 1\n" +
            "game queens\n" +
            "technique-set 3\n" +
            "cutoff 2026-11-18\n" +
            "101 line-lock=2 singles=4\n" +
            "102 singles=6\n"

        assertEquals(expected, TechniqueIndex.write(shuffled))
        assertEquals(expected, TechniqueIndex.write(sorted))
    }

    @Test
    fun `a puzzle walked with no listed technique is written bare and read back empty`() {
        val text = TechniqueIndex.write(index(mapOf(7 to emptyMap<TechniqueId, Int>(), 8 to mapOf(singles to 1))))

        assertEquals("technique-index 1\ngame queens\ntechnique-set 3\ncutoff 2026-11-18\n7\n8 singles=1\n", text)
        assertEquals(emptyMap<TechniqueId, Int>(), TechniqueIndex.read(text).getOrNull()?.entries?.get(7))
    }

    @Test
    fun `read ignores blank lines`() {
        val text = "\ntechnique-index 1\n\ngame queens\ntechnique-set 3\ncutoff 2026-11-18\n\n101 singles=4\n\n"

        assertEquals(index(mapOf(101 to mapOf(singles to 4))), TechniqueIndex.read(text).getOrNull())
    }

    @Test
    fun `a newer schema is unsupported and never half-read`() {
        val result = TechniqueIndex.read("technique-index 2\ngame queens\nsomething new\n")

        assertEquals(DecodeResult.Err(DecodeError.UnsupportedVersion(2, 1)), result)
    }

    @Test
    fun `a broken count is malformed`() {
        val head = "technique-index 1\ngame queens\ntechnique-set 3\ncutoff 2026-11-18\n"

        malformed(head + "101 singles=x\n")
        malformed(head + "101 singles=0\n")
        malformed(head + "101 singles\n")
        malformed(head + "101 Singles=1\n")
        malformed(head + "abc singles=1\n")
    }

    @Test
    fun `a duplicate puzzle number is malformed`() {
        val text = "technique-index 1\ngame queens\ntechnique-set 3\ncutoff 2026-11-18\n101 singles=1\n101 singles=2\n"

        assertTrue(malformed(text).contains("101"))
    }

    @Test
    fun `a technique listed twice on one line is malformed`() {
        malformed("technique-index 1\ngame queens\ntechnique-set 3\ncutoff 2026-11-18\n101 singles=1 singles=2\n")
    }

    @Test
    fun `a missing or misordered header is malformed`() {
        malformed("")
        malformed("101 singles=1\n")
        malformed("technique-index 1\ngame queens\ncutoff 2026-11-18\ntechnique-set 3\n")
        malformed("technique-index 1\ngame queens\ntechnique-set 3\n")
        malformed("technique-index 1\ngame queens\ntechnique-set 3\ncutoff not-a-day\n")
        malformed("technique-index 0\ngame queens\ntechnique-set 3\ncutoff 2026-11-18\n")
    }

    @Test
    fun `write refuses a technique id the file cannot hold`() {
        try {
            TechniqueIndex.write(index(mapOf(1 to mapOf(TechniqueId("Line Lock") to 1))))
            assertTrue("expected IllegalArgumentException", false)
        } catch (expected: IllegalArgumentException) {
            // a bad id is a programming error, never a file that reads back differently
        }
    }

    @Test
    fun `write refuses a count below one`() {
        try {
            TechniqueIndex.write(index(mapOf(1 to mapOf(singles to 0))))
            assertTrue("expected IllegalArgumentException", false)
        } catch (expected: IllegalArgumentException) {
            // read rejects it as Malformed, so write must never produce it
        }
    }
}
