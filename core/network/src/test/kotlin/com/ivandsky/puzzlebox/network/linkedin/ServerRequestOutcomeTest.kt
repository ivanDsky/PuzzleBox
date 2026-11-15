package com.ivandsky.puzzlebox.network.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead.Rejected.Kind
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSendResult
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSendResult.Rejected
import org.junit.Assert.assertEquals
import org.junit.Test

class ServerRequestOutcomeTest {

    private val win = LinkedInPostFixtures.text("zip-update-game-state-win.response.rsc")
    private val firstMove = LinkedInPostFixtures.text("zip-update-game-state-first-move.response.rsc")

    @Test
    fun `a 200 whose server response has no errors is Posted`() {
        assertEquals(LinkedInSendResult.Posted, serverRequestOutcome(200, win))
        assertEquals(LinkedInSendResult.Posted, serverRequestOutcome(200, firstMove))
    }

    @Test
    fun `a 200 with errors is Unexpected`() {
        val failed = win.replace("\"errors\":[]", "\"errors\":[{\"message\":\"nope\"}]")

        assertEquals(Rejected(Kind.Unexpected), serverRequestOutcome(200, failed))
    }

    @Test
    fun `a 200 that is not a server response is Unexpected`() {
        listOf(
            "",
            "<html>Sign in</html>",
            "1:[]\n",
            "0:{not json",
            "0:[]",
            "0:{\"response\":{}}",
            "0:{\"response\":{\"errors\":\"none\"}}",
            "0:{\"response\":{\"errors\":null}}",
        ).forEach { assertEquals(it, Rejected(Kind.Unexpected), serverRequestOutcome(200, it)) }
    }

    @Test
    fun `the server response line is found wherever it sits in the stream`() {
        assertEquals(LinkedInSendResult.Posted, serverRequestOutcome(200, "2:\"x\"\n$win\n3:[]"))
    }

    @Test
    fun `409 is AlreadyPlayed whatever the body says`() {
        assertEquals(LinkedInSendResult.AlreadyPlayed, serverRequestOutcome(409, ""))
        assertEquals(LinkedInSendResult.AlreadyPlayed, serverRequestOutcome(409, win))
    }

    @Test
    fun `other statuses follow the shared table and never read the body`() {
        assertEquals(Rejected(Kind.SessionExpired), serverRequestOutcome(401, win))
        assertEquals(Rejected(Kind.SessionExpired), serverRequestOutcome(403, win))
        assertEquals(Rejected(Kind.RateLimited), serverRequestOutcome(429, win))
        assertEquals(Rejected(Kind.Challenge), serverRequestOutcome(999, win))
        assertEquals(Rejected(Kind.Challenge), serverRequestOutcome(302, win))
        assertEquals(Rejected(Kind.Unexpected), serverRequestOutcome(500, win))
        assertEquals(Rejected(Kind.Unexpected), serverRequestOutcome(404, ""))
    }
}
