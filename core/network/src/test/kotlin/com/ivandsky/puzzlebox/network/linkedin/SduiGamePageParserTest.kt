package com.ivandsky.puzzlebox.network.linkedin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SduiGamePageParserTest {

    private val page = LinkedInPostFixtures.text("zip-page-daily-solved.rsc")

    @Test
    fun `the solved daily page parses to its served state`() {
        assertEquals(LinkedInPostFixtures.dailyPage, SduiGamePageParser.parse(page))
    }

    @Test
    fun `the unsolved daily page parses as not won, though it carries completion attributes`() {
        val unsolved = LinkedInPostFixtures.text("zip-page-daily-unsolved.rsc")

        assertEquals(LinkedInPostFixtures.unsolvedDailyPage, SduiGamePageParser.parse(unsolved))
    }

    @Test
    fun `an explicit gameBoardHasWon state wins over the completion record`() {
        val explicit = page.replace(
            "{\"key\":{\"key\":{\"value\":{\"\$case\":\"id\",\"id\":\"gameBoardUndoDisabled\"}}}",
            "{\"key\":{\"key\":{\"value\":{\"\$case\":\"id\",\"id\":\"gameBoardHasWon\"}}},\"value\":{\"\$case\":\"booleanValue\",\"booleanValue\":false}}," +
                "{\"key\":{\"key\":{\"value\":{\"\$case\":\"id\",\"id\":\"gameBoardUndoDisabled\"}}}",
        )

        assertEquals(false, SduiGamePageParser.parse(explicit)!!.hasWon)
    }

    @Test
    fun `a page missing any one required state is drift`() {
        REQUIRED_STATES.forEach { key ->
            val drifted = page.replace("\"id\":\"$key\"}}},\"value\"", "\"id\":\"${key}Renamed\"}}},\"value\"")

            assertNull(key, SduiGamePageParser.parse(drifted))
        }
    }

    @Test
    fun `a bigint that is not an RSC bigint is malformed`() {
        assertNull(SduiGamePageParser.parse(page.replace("\"\$n608\"", "\"608\"")))
        assertNull(SduiGamePageParser.parse(page.replace("\"\$n608\"", "\"\$nabc\"")))
        assertNull(SduiGamePageParser.parse(page.replace("\"\$n608\"", "\"\$n99999999999999999999\"")))
    }

    @Test
    fun `a flag or count of the wrong type is malformed`() {
        val tutorial = "\"id\":\"gameIsTutorialGameBinding\"}}},\"value\":{\"\$case\":\"booleanValue\",\"booleanValue\":false}"

        assertNull(SduiGamePageParser.parse(page.replace(tutorial, tutorial.replace("false", "\"no\""))))
        assertNull(SduiGamePageParser.parse(page.replace("\"intValue\":597", "\"intValue\":\"597\"")))
    }

    @Test
    fun `something that is not an RSC stream is drift`() {
        assertNull(SduiGamePageParser.parse(""))
        assertNull(SduiGamePageParser.parse("<html><body>Sign in</body></html>"))
    }

    private companion object {
        val REQUIRED_STATES = listOf(
            "gameBoardPuzzleId",
            "gameBoardProfileId",
            "gameIsTutorialGameBinding",
            "gameBoardTotalPlaysCount",
            "gameBoardWinCount",
            "gameBoardGameRequestTime",
        )
    }
}
