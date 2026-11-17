package com.ivandsky.puzzlebox.network.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead.Rejected.Kind
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSendResult
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSendResult.Rejected
import org.junit.Assert.assertEquals
import org.junit.Test

class VoyagerSaveOutcomeTest {

    private val win = MiniSudokuVoyagerFixtures.winResponse

    @Test
    fun `a 200 carrying the save's own result is Posted`() {
        assertEquals(LinkedInSendResult.Posted, voyagerSaveOutcome(200, win))
    }

    @Test
    fun `a 200 that does not carry the save's result is Unexpected`() {
        listOf(
            "",
            "<html>Sign in</html>",
            "{not json",
            "[]",
            "{}",
            """{"data":{}}""",
            """{"data":{"data":{}}}""",
            """{"data":{"data":{"updateIdentityDashGames":null}}}""",
            """{"data":{"data":{"updateIdentityDashGames":"ok"}}}""",
            """{"data":{"data":null},"errors":[{"message":"nope"}]}""",
            """{"updateIdentityDashGames":{}}""",
        ).forEach { assertEquals(it, Rejected(Kind.Unexpected), voyagerSaveOutcome(200, it)) }
    }

    @Test
    fun `every other status follows the shared table whatever the body says`() {
        assertEquals(Rejected(Kind.SessionExpired), voyagerSaveOutcome(401, win))
        assertEquals(Rejected(Kind.SessionExpired), voyagerSaveOutcome(403, win))
        assertEquals(Rejected(Kind.RateLimited), voyagerSaveOutcome(429, win))
        assertEquals(Rejected(Kind.Challenge), voyagerSaveOutcome(999, win))
        assertEquals(Rejected(Kind.Challenge), voyagerSaveOutcome(302, win))
        assertEquals(Rejected(Kind.Unexpected), voyagerSaveOutcome(400, win))
        assertEquals(Rejected(Kind.Unexpected), voyagerSaveOutcome(500, win))
    }

    @Test
    fun `a 409 is never a post on its own word`() {
        assertEquals(Rejected(Kind.Unexpected), voyagerSaveOutcome(409, win))
    }
}
