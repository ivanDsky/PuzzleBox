package com.ivandsky.puzzlebox.network.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInBoard
import com.ivandsky.puzzlebox.game.linkedin.LinkedInBoard.Row
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Pins the board shape LinkedIn returns for the connections query, hidden rows included. */
class VoyagerBoardParserTest {

    private val board: LinkedInBoard = requireNotNull(VoyagerBoardParser.parse(fixture("board.json")))

    @Test
    fun `a ranked board returns names, times and flags in response order`() {
        assertEquals(
            listOf("ACoAAFAKE0001", "ACoAAFAKE0002", "ACoAAFAKE0003", "ACoAAFAKE0004", "ACoAAFAKE0009"),
            board.rows.map { it.memberRef },
        )
        assertEquals(Row("ACoAAFAKE0001", "Pat Example", 14, true, false, PAT_PHOTO), board.rows[0])
        assertEquals(Row("ACoAAFAKE0002", "Anna Sample", 21, false, false, ANNA_PHOTO), board.rows[1])
        assertEquals(Row("ACoAAFAKE0003", "Lee( Q) Test", 28, true, true), board.rows[2])
        assertEquals(Row("ACoAAFAKE0009", "", 57, false, false), board.rows[4])
    }

    @Test
    fun `a hidden row has no time and no flags`() {
        val hidden = board.rows.single { it.memberRef == "ACoAAFAKE0004" }

        assertEquals(Row("ACoAAFAKE0004", "Hidden Person", null, false, false), hidden)
        assertNull(hidden.timeSeconds)
    }

    @Test
    fun `the player's own row stays in the output`() {
        assertNotNull(board.rows.firstOrNull { it.memberRef == "ACoAAFAKE0001" })
    }

    @Test
    fun `a row without a profile record keeps its id with an empty name`() {
        assertEquals("", board.rows.single { it.memberRef == "ACoAAFAKE0009" }.name)
    }

    @Test
    fun `the photo is the smallest artifact of the profile picture, root url plus path segment`() {
        assertEquals(PAT_PHOTO, board.rows[0].pictureUrl)
    }

    @Test
    fun `the framed reference is the fallback when there is no resolved one`() {
        assertEquals(ANNA_PHOTO, board.rows[1].pictureUrl)
    }

    @Test
    fun `no picture, a picture of an odd shape or a row without a profile record is a null photo`() {
        assertNull(board.rows[2].pictureUrl)
        assertNull(board.rows[3].pictureUrl)
        assertNull(board.rows[4].pictureUrl)
    }

    @Test
    fun `a picture without a usable artifact leaves the rest of the row alone`() {
        val body = wrap(
            row("ACoAAFAKE0001", ranking = "1", score = """{"timeElapsed":9}""", hintFree = "true"),
            profiles = """{"entityUrn":"urn:li:fsd_profile:ACoAAFAKE0001","firstName":"Pat","lastName":"Example",""" +
                """"profilePicture":{"displayImageReferenceResolutionResult":{"vectorImage":""" +
                """{"rootUrl":"https://media.licdn.com/x_","artifacts":[{"width":"wide"},{"height":5},""" +
                """{"width":100,"fileIdentifyingUrlPathSegment":7}]}}}}""",
        )

        val row = requireNotNull(VoyagerBoardParser.parse(body)).rows.single()

        assertEquals(Row("ACoAAFAKE0001", "Pat Example", 9, true, false), row)
    }

    @Test
    fun `an artifact list that is empty or a root url that is missing is a null photo`() {
        val empty = """{"rootUrl":"https://media.licdn.com/x_","artifacts":[]}"""
        val noRoot = """{"artifacts":[{"width":100,"fileIdentifyingUrlPathSegment":"a"}]}"""

        listOf(empty, noRoot).forEach { vector ->
            val body = wrap(
                row("ACoAAFAKE0001", ranking = "1", score = """{"timeElapsed":9}""", hintFree = "true"),
                profiles = """{"entityUrn":"urn:li:fsd_profile:ACoAAFAKE0001","firstName":"Pat",""" +
                    """"profilePicture":{"displayImageReferenceResolutionResult":{"vectorImage":$vector}}}""",
            )

            assertNull(requireNotNull(VoyagerBoardParser.parse(body)).rows.single().pictureUrl)
        }
    }

    @Test
    fun `an empty board is an empty list`() {
        val body = """{"data":{"data":{"$ROOT":{"paging":{"total":0},"elements":[]}}},"included":[]}"""

        assertEquals(LinkedInBoard(emptyList()), VoyagerBoardParser.parse(body))
    }

    @Test
    fun `a ranked row with a zero or missing time is hidden`() {
        val body = wrap(
            row("ACoAAFAKE0001", ranking = "1", score = """{"timeElapsed":0}""", hintFree = "true"),
            row("ACoAAFAKE0002", ranking = "1", score = """{"timeElapsed":null}""", hintFree = "true"),
        )

        val rows = requireNotNull(VoyagerBoardParser.parse(body)).rows

        assertEquals(listOf(null, null), rows.map { it.timeSeconds })
        assertEquals(listOf(false, false), rows.map { it.hintFree })
    }

    @Test
    fun `rows without a usable profile pointer are dropped, and a board of only such rows is a shape mismatch`() {
        val good = row("ACoAAFAKE0001", ranking = "1", score = """{"timeElapsed":9}""", hintFree = "false")
        val noPointer = """{"ranking":2,"gameScore":{"timeElapsed":10},"playerDetails":{"player":{}}}"""
        val badPrefix = row("x", ranking = "3", score = """{"timeElapsed":11}""", hintFree = "false")
            .replace("urn:li:fsd_profile:x", "urn:li:member:x")

        assertEquals(1, requireNotNull(VoyagerBoardParser.parse(wrap(good, noPointer))).rows.size)
        assertNull(VoyagerBoardParser.parse(wrap(noPointer, badPrefix)))
    }

    @Test
    fun `a body of the wrong shape is a shape mismatch`() {
        assertNull(VoyagerBoardParser.parse("not json"))
        assertNull(VoyagerBoardParser.parse("{}"))
        assertNull(VoyagerBoardParser.parse("""{"data":{"data":{"$ROOT":{"paging":{"total":0}}}}}"""))
        assertNull(VoyagerBoardParser.parse("""{"data":{"data":{"$ROOT":{"elements":{}}}}}"""))
    }

    private fun row(id: String, ranking: String, score: String, hintFree: String) =
        """{"ranking":$ranking,"gameScore":$score,"completionAttributes":{"isHintFree":$hintFree},""" +
            """"isFlawless":false,"playerDetails":{"player":{"*profile":"urn:li:fsd_profile:$id"}}}"""

    private fun wrap(vararg rows: String, profiles: String = "") =
        """{"data":{"data":{"$ROOT":{"paging":{"total":${rows.size}},"elements":[${rows.joinToString(",")}]}}},"included":[$profiles]}"""

    private fun fixture(name: String): String =
        requireNotNull(javaClass.classLoader).getResource("fixtures/linkedin-account/$name")!!.readText()

    private companion object {
        const val ROOT = "identityDashGameConnectionsEntitiesByOptedInToLeaderboardAndPlayed"
        const val PAT_PHOTO = "https://media.licdn.com/dms/image/v2/FAKE0001/profile-displayphoto-shrink_100_100/0/fake-100?e=1&t=a"
        const val ANNA_PHOTO = "https://media.licdn.com/dms/image/v2/FAKE0002/framed-displayphoto-shrink_100_100/0/framed-100?e=1&t=b"
    }
}
