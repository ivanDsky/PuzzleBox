package com.ivandsky.puzzlebox.network.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInProgress
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
    fun `the unsolved daily page is Fresh, though it carries completion attributes`() {
        val unsolved = LinkedInPostFixtures.text("zip-page-daily-unsolved.rsc")

        assertEquals(LinkedInPostFixtures.unsolvedDailyPage, SduiGamePageParser.parse(unsolved))
    }

    @Test
    fun `the onboarding tutorial page is a tutorial, Fresh, with the tutorial's puzzle id and not the day's`() {
        val tutorial = SduiGamePageParser.parse(LinkedInPostFixtures.text("zip-page-test-account-tutorial-2.rsc"))!!

        assertEquals(true, tutorial.isTutorial)
        assertEquals(2L, tutorial.puzzleId)
        assertEquals(LinkedInProgress.Fresh, tutorial.progress)
    }

    @Test
    fun `the derived in-progress page is Started with the plays after the save`() {
        val saved = LinkedInPostFixtures.text("zip-page-daily-in-progress.derived.rsc")

        assertEquals(LinkedInPostFixtures.inProgressDailyPage, SduiGamePageParser.parse(saved))
    }

    @Test
    fun `an explicit gameBoardHasWon true is Won on any page`() {
        val unsolved = LinkedInPostFixtures.text("zip-page-daily-unsolved.rsc")
        val explicit = unsolved.replace(
            "{\"key\":{\"key\":{\"value\":{\"\$case\":\"id\",\"id\":\"gameBoardUndoDisabled\"}}}",
            "{\"key\":{\"key\":{\"value\":{\"\$case\":\"id\",\"id\":\"gameBoardHasWon\"}}},\"value\":{\"\$case\":\"booleanValue\",\"booleanValue\":true}}," +
                "{\"key\":{\"key\":{\"value\":{\"\$case\":\"id\",\"id\":\"gameBoardUndoDisabled\"}}}",
        )

        assertEquals(LinkedInProgress.Won, SduiGamePageParser.parse(explicit)!!.progress)
    }

    @Test
    fun `a saved state of an unknown kind is Won`() {
        val unknown = page.replace("\"\$case\":\"trailGameState\",\"trailGameState\"", "\"\$case\":\"gridGameState\",\"gridGameState\"")

        assertEquals(LinkedInProgress.Won, SduiGamePageParser.parse(unknown)!!.progress)
    }

    @Test
    fun `a saved trail with every cell is Won and one short is Started`() {
        val saved = LinkedInPostFixtures.text("zip-page-daily-in-progress.derived.rsc")
        fun withCells(count: Int) = saved.replace("\"cells\":[7,8]", "\"cells\":[" + (0 until count).joinToString(",") + "]")

        assertEquals(LinkedInProgress.Won, SduiGamePageParser.parse(withCells(36))!!.progress)
        assertEquals(LinkedInProgress.Started, SduiGamePageParser.parse(withCells(35))!!.progress)
    }

    @Test
    fun `the unsolved Tango page is Fresh with its puzzle id`() {
        val parsed = SduiGamePageParser.parse(LinkedInPostFixtures.text("tango-page-daily-unsolved.rsc"))!!

        assertEquals(770L, parsed.puzzleId)
        assertEquals(LinkedInProgress.Fresh, parsed.progress)
    }

    @Test
    fun `the Tango page reloaded after a partial save is Started with the plays after the save`() {
        val parsed = SduiGamePageParser.parse(LinkedInPostFixtures.text("tango-page-daily-in-progress.rsc"))!!

        assertEquals(LinkedInProgress.Started, parsed.progress)
        assertEquals(595L, parsed.totalPlays)
        assertEquals(594L, parsed.wins)
    }

    @Test
    fun `a saved Tango board with no unknown cell is Won`() {
        val saved = LinkedInPostFixtures.text("tango-page-daily-in-progress.rsc")
        val full = saved.replace("LotkaCellValue_UNKNOWN", "LotkaCellValue_ZERO")

        assertEquals(LinkedInProgress.Won, SduiGamePageParser.parse(full)!!.progress)
    }

    @Test
    fun `a page with no saved-state field is drift`() {
        assertNull(SduiGamePageParser.parse(page.replace("mostRecentGameState", "renamedState")))
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
