package com.ivandsky.puzzlebox.network.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The voyager read of today's game against the unplayed Mini Sudoku capture and the two reads derived from it. */
class VoyagerGamePageParserTest {

    @Test
    fun `the unplayed capture is a fresh page of puzzle 463 for the member in its URN`() {
        assertEquals(MiniSudokuVoyagerFixtures.unplayedPage, VoyagerGamePageParser.parse(MiniSudokuVoyagerFixtures.unplayedGame))
    }

    @Test
    fun `a stored record in progress is Started`() {
        assertEquals(MiniSudokuVoyagerFixtures.inProgressPage, VoyagerGamePageParser.parse(MiniSudokuVoyagerFixtures.inProgressGame))
    }

    @Test
    fun `a solved stored record is Won`() {
        assertEquals(MiniSudokuVoyagerFixtures.solvedPage, VoyagerGamePageParser.parse(MiniSudokuVoyagerFixtures.solvedGame))
    }

    @Test
    fun `a play state not known here is Won, so nothing is sent over it`() {
        listOf("END_FAILED", "PAUSED", "").forEach { state ->
            assertEquals(state, LinkedInProgress.Won, VoyagerGamePageParser.parse(game(playState = "\"$state\""))?.progress)
        }
    }

    @Test
    fun `the game is the included entry the first element names, wherever it sits`() {
        val other = """{"entityUrn":"urn:li:fsd_game:(ACoAAFAKE0002,7,463)","puzzleId":999,"gameStoredRecord":{"gamePlayState":"END_SOLVED"}}"""
        val body = response(included = "$other,${record()}")

        val page = VoyagerGamePageParser.parse(body)

        assertEquals(463L, page?.puzzleId)
        assertEquals(LinkedInProgress.Fresh, page?.progress)
    }

    @Test
    fun `counts and the request time the record leaves out are zero, and a tutorial is told apart`() {
        val bare = """{"entityUrn":"$URN","puzzleId":463,"isTutorialGame":true,"gameStoredRecord":{"gamePlayState":"UNKNOWN","gameRequestTime":17}}"""

        val page = VoyagerGamePageParser.parse(response(included = bare))

        assertEquals(MiniSudokuVoyagerFixtures.unplayedPage.copy(isTutorial = true, totalPlays = 0, wins = 0, requestTime = 17), page)
    }

    @Test
    fun `a body without a readable game and stored record is a shape mismatch`() {
        val bodies = listOf(
            "",
            "not json",
            "<html>Sign in</html>",
            "{}",
            response(elements = "[]"),
            response(elements = "[42]"),
            response(elements = """["urn:li:fsd_profile:ACoAAFAKE0001"]"""),
            response(included = ""),
            response(included = record(urn = "urn:li:fsd_game:(ACoAAFAKE0002,7,463)")),
            response(included = """{"entityUrn":"$URN","puzzleId":463}"""),
            response(included = """{"entityUrn":"$URN","puzzleId":463,"gameStoredRecord":null}"""),
            game(playState = "null"),
            game(playState = "7"),
            response(included = record(puzzleId = "null")),
            response(included = record(puzzleId = "\"463\"")),
        )

        bodies.forEach { assertNull(it, VoyagerGamePageParser.parse(it)) }
    }

    private fun game(playState: String) = response(included = record(playState = playState))

    private fun record(urn: String = URN, puzzleId: String = "463", playState: String = "\"UNKNOWN\"") =
        """{"entityUrn":"$urn","puzzleId":$puzzleId,"winCount":2,"personalStats":{"totalPlaysCount":2},"isTutorialGame":false,""" +
            """"gameStoredRecord":{"gamePlayState":$playState,"gameRequestTime":null}}"""

    private fun response(elements: String = """["$URN"]""", included: String = record()) =
        """{"data":{"data":{"identityDashGamesByTodaysGame":{"*elements":$elements}}},"included":[$included]}"""

    private companion object {
        const val URN = "urn:li:fsd_game:(ACoAAFAKEPROFILE0000000000000000000000000,7,463)"
    }
}
