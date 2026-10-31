package com.ivandsky.puzzlebox.data.network

import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.games.zip.codec.ZipWireParser
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rotation tolerance: the extractor finds the data wherever LinkedIn puts it, or nothing. */
class TrailGameExtractorTest {

    private val date = LocalDate.of(2026, 10, 30)

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader).getResource("fixtures/$name")!!.readText()

    @Test
    fun `extracts trailGamePuzzle and edition from the RSC flight stream`() {
        val match = TrailGameExtractor.extract(fixture("linkedin-zip-rsc-592.txt"))

        assertNotNull(match)
        assertEquals("592", match!!.editionText)
        // The extraction feeds ZipWireParser's input — prove it end to end (A16 shape).
        val daily = ZipWireParser.parse(match.trailGamePuzzleJson, date, match.editionText)
        assertTrue(daily is DecodeResult.Ok)
        daily as DecodeResult.Ok
        assertEquals(6, daily.value.puzzle.board.n)
        assertEquals(4, daily.value.puzzle.board.walls.size)
        assertEquals(36, daily.value.puzzle.solution!!.size)
        assertEquals(592, daily.value.ref.puzzleNumber)
    }

    @Test
    fun `spa shell mention without a data object yields null`() {
        assertNull(TrailGameExtractor.extract(fixture("linkedin-zip-page-shell.html")))
    }

    @Test
    fun `empty and junk bodies yield null`() {
        assertNull(TrailGameExtractor.extract(""))
        assertNull(TrailGameExtractor.extract("not json at all"))
        assertNull(TrailGameExtractor.extract("\"trailGamePuzzle\" followed by prose"))
        assertNull(TrailGameExtractor.extract("{\"trailGamePuzzle\": {unclosed"))
    }

    @Test
    fun `object with a non-numeric gridSize is skipped, later data still wins`() {
        val body = """
            first:{"trailGamePuzzle":{"gridSize":"big","note":"decoy"}},
            then:{"trailGamePuzzle":{"gridSize":4,"orderedSequence":[0,15],"solution":[0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15]}},
            meta:{"todaysGameEditionText":"424"}
        """.trimIndent()
        val match = TrailGameExtractor.extract(body)
        assertNotNull(match)
        assertTrue(match!!.trailGamePuzzleJson.contains("\"gridSize\":4"))
        assertEquals("424", match.editionText)
    }

    @Test
    fun `escaped braces inside strings do not break the brace match`() {
        val body = """
            {"trailGamePuzzle":{"gridSize":2,"note":"closing } and quote \" inside","orderedSequence":[0,3]},"todaysGameEditionText":"12"}
        """.trimIndent()
        val match = TrailGameExtractor.extract(body)
        assertNotNull(match)
        assertTrue(match!!.trailGamePuzzleJson.endsWith("}"))
        assertEquals("12", match.editionText)
    }

    @Test
    fun `edition text with unicode escapes decodes`() {
        val body = """{"trailGamePuzzle":{"gridSize":2},"todaysGameEditionText":"592\u2713"}"""
        val match = TrailGameExtractor.extract(body)
        assertEquals("592✓", match!!.editionText)
    }
}
