package com.ivandsky.puzzlebox.games.tango.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Key-scan semantics (research 01: the payload path has moved before — accept any body). */
class TangoGameExtractorTest {

    private fun pageCarryingData(): String =
        """{"tangoGamePuzzle":{"gridSize":6,"given":[["S"]]},"todaysGameEditionText":"301"}"""

    @Test
    fun `extracts the data-bearing object from a flight-stream line`() {
        val match = TangoGameExtractor.extract(javaClass.getResource("/fixtures/linkedin-tango-rsc-301.txt")!!.readText())
        requireNotNull(match)
        assertTrue(match.tangoGamePuzzleJson.startsWith("""{"gridSize":6"""))
        assertEquals("301", match.editionText)
    }

    @Test
    fun `extracts from a bare JSON response`() {
        val match = TangoGameExtractor.extract(pageCarryingData())
        requireNotNull(match)
        assertEquals("301", match.editionText)
    }

    @Test
    fun `a code mention of the key without numeric gridSize never wins`() {
        assertNull(TangoGameExtractor.extract("""var k = "tangoGamePuzzle"; // no data"""))
        assertNull(TangoGameExtractor.extract(""""tangoGamePuzzle": "a string, not an object""""))
        assertNull(TangoGameExtractor.extract("""{"tangoGamePuzzle":{"gridSize":"six"}}"""))
    }

    @Test
    fun `returns null on bodies without the key`() {
        assertNull(TangoGameExtractor.extract("""{"trailGamePuzzle":{"gridSize":6}}"""))
        assertNull(TangoGameExtractor.extract(""))
    }
}
