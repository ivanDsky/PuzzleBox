package com.ivandsky.puzzlebox.network.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInOwnResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoyagerOwnResultParserTest {

    private val fixture = requireNotNull(javaClass.classLoader)
        .getResource("fixtures/linkedin-account/end-page.json")!!.readText()

    private fun parse(gameType: Int, number: Int, body: String = fixture) =
        VoyagerOwnResultParser.parse(body, VoyagerQueries.gameUrn("ACoAAFAKE0001", gameType, number))

    @Test
    fun `a solved day returns the time and hint-free flag`() {
        // The 6,538 record carries puzzleId 539 (today's); the requested URN, not that field, names the day.
        assertEquals(LinkedInOwnResult.Solved(14, hintFree = true), parse(6, 538))
        assertEquals(LinkedInOwnResult.Solved(95, hintFree = false), parse(3, 860))
    }

    @Test
    fun `a day that was not started is not played`() {
        assertEquals(LinkedInOwnResult.NotPlayed, parse(5, 700))
    }

    @Test
    fun `a zero score is not played`() {
        assertEquals(LinkedInOwnResult.NotPlayed, parse(7, 392))
    }

    @Test
    fun `a null score is not played`() {
        assertEquals(LinkedInOwnResult.NotPlayed, parse(4, 91))
    }

    @Test
    fun `a URN the response does not contain is a shape mismatch`() {
        assertNull(parse(6, 537))
    }

    @Test
    fun `a body of the wrong shape is a shape mismatch`() {
        val bodies = listOf(
            "not json",
            "{}",
            """{"included": {}}""",
            """{"included": [{"entityUrn": "x"}]}""",
        )

        bodies.forEach { assertNull(it, parse(6, 538, it)) }
    }
}
