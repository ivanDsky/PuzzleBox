package com.ivandsky.puzzlebox.games.tango.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Key-scan semantics (research 02: the same `lotkaGamePuzzle` key in stream and by-id bodies). */
class TangoGameExtractorTest {

    private fun fixture(name: String) = javaClass.getResource("/fixtures/$name")!!.readText()

    @Test
    fun `extracts the data-bearing object from the live flight stream, past the case mention`() {
        val match = requireNotNull(TangoGameExtractor.extract(fixture("01-tango-stream-today.txt")))

        assertTrue(match.lotkaGamePuzzleJson, match.lotkaGamePuzzleJson.contains("\"presetCellIdxes\":[0,1,2,7,25,30,31,32]"))
        assertEquals("759", match.editionText)
    }

    @Test
    fun `extracts from a by-id GraphQL body, which carries no edition text`() {
        val match = requireNotNull(TangoGameExtractor.extract(fixture("linkedin-tango-byid-301.json")))

        assertTrue(match.lotkaGamePuzzleJson.contains("\"presetCellIdxes\""))
        assertNull(match.editionText)
    }

    @Test
    fun `extracts from a bare JSON response`() {
        val match = requireNotNull(TangoGameExtractor.extract("""{"lotkaGamePuzzle" : {"gridSize":6},"todaysGameEditionText":"759"}"""))

        assertEquals("""{"gridSize":6}""", match.lotkaGamePuzzleJson)
        assertEquals("759", match.editionText)
    }

    @Test
    fun `a mention of the key without its own data object never wins`() {
        assertNull(TangoGameExtractor.extract("""var k = "lotkaGamePuzzle"; // no data"""))
        assertNull(TangoGameExtractor.extract(""""lotkaGamePuzzle": "a string, not an object""""))
        assertNull(TangoGameExtractor.extract("""{"lotkaGamePuzzle":{"gridSize":"six"}}"""))
        // A null union member must not borrow the next game's object.
        assertNull(TangoGameExtractor.extract("""{"lotkaGamePuzzle":null,"queensGamePuzzle":{"gridSize":8}}"""))
    }

    @Test
    fun `returns null on bodies without the key`() {
        assertNull(TangoGameExtractor.extract("""{"trailGamePuzzle":{"gridSize":6}}"""))
        assertNull(TangoGameExtractor.extract(""))
    }
}
