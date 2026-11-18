package com.ivandsky.puzzlebox.data.linkedin

import com.ivandsky.puzzlebox.data.linkedin.LinkedInPostEligibility.Facts
import com.ivandsky.puzzlebox.data.linkedin.LinkedInPostEligibility.Verdict
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.CompletionSource
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.game.linkedin.LinkedInPlay
import com.ivandsky.puzzlebox.game.linkedin.LinkedInPlayValue
import com.ivandsky.puzzlebox.game.linkedin.LinkedInPost
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead.Rejected.Kind
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.testing.game.TestGames
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The one rule behind the button and the submit guard: which solves may be posted, and what blocks the rest. */
class LinkedInPostEligibilityTest {

    private val zip = GameId("zip")
    private val day = LocalDate.of(2026, 11, 5)
    private val play = LinkedInPlay(
        states = mapOf("zipGamePathBinding" to LinkedInPlayValue.Strings(listOf("0", "1"))),
        payloadKeys = mapOf("playRecord" to "zipGamePathBinding"),
        mistakeFree = true,
    )
    private val solve = CompletionRecord(
        puzzleRef = TestGames.Ref(zip, PuzzleKey.Daily(day, 512)),
        time = 83_000L,
        hints = 0,
        stats = RecordStats(1, "{}"),
        isRecord = true,
    )
    private val postable = Facts(
        posting = true,
        link = LinkState.Connected("me"),
        record = solve,
        typeId = 6,
        gamePath = "games/zip/",
        delta = 0,
        posted = false,
        sending = false,
    )
    private val hidden = Verdict.Blocked(LinkedInPostState.Hidden)

    private suspend fun decide(facts: Facts, encoded: LinkedInPlay? = play): Verdict =
        LinkedInPostEligibility.decide(facts) { encoded }

    @Test
    fun `today's own solve with posting on is open and carries the solve as the post`() = runTest {
        assertEquals(
            Verdict.Open("me", LinkedInPost("games/zip/", 6, 512, 83_000L, hintUsed = false, play = play), unsure = false),
            decide(postable),
        )
    }

    @Test
    fun `a solve that took a hint is posted as hint used`() = runTest {
        val verdict = decide(postable.copy(record = solve.copy(hints = 2))) as Verdict.Open

        assertEquals(true, verdict.post.hintUsed)
        assertEquals(false, verdict.post.flawless)
    }

    @Test
    fun `each missing piece hides the button on its own`() = runTest {
        val cases = mapOf(
            "posting off" to postable.copy(posting = false),
            "not linked" to postable.copy(link = LinkState.NotConnected),
            "no solve" to postable.copy(record = null),
            "an import" to postable.copy(record = solve.copy(source = CompletionSource.LinkedIn)),
            "a duel leg" to postable.copy(record = solve.copy(matchCode = "DUEL42")),
            "a solve with no puzzle number" to
                postable.copy(record = solve.copy(puzzleRef = TestGames.Ref(zip, PuzzleKey.Daily(day, null)))),
            "no type id" to postable.copy(typeId = null),
            "no game path" to postable.copy(gamePath = null),
        )

        assertEquals(cases.mapValues { hidden }, cases.mapValues { (_, facts) -> decide(facts) })
    }

    @Test
    fun `a solve whose replay is missing or does not encode is hidden, also while the link is paused`() = runTest {
        assertEquals(hidden, decide(postable, encoded = null))
        assertEquals(hidden, decide(postable.copy(link = LinkState.Paused("me", Kind.Challenge)), encoded = null))
    }

    @Test
    fun `the replay is only encoded for a solve that could still be sent`() = runTest {
        val neverEncoded = listOf(
            postable.copy(posting = false),
            postable.copy(record = solve.copy(source = CompletionSource.LinkedIn)),
            postable.copy(posted = true),
            postable.copy(delta = 1),
        )
        var encodes = 0

        neverEncoded.forEach { facts ->
            LinkedInPostEligibility.decide(facts) {
                encodes++
                play
            }
        }

        assertEquals(0, encodes)
    }

    @Test
    fun `a posted day stays posted on a later day and while paused, but not with posting off`() = runTest {
        val posted = Verdict.Blocked(LinkedInPostState.Posted)

        assertEquals(posted, decide(postable.copy(posted = true)))
        assertEquals(posted, decide(postable.copy(posted = true, delta = 3)))
        assertEquals(posted, decide(postable.copy(posted = true, link = LinkState.Paused("me", Kind.RateLimited))))
        assertEquals(hidden, decide(postable.copy(posted = true, posting = false)))
    }

    @Test
    fun `any day but the game's today is not today, whatever the link`() = runTest {
        val notToday = Verdict.Blocked(LinkedInPostState.NotToday)

        assertEquals(notToday, decide(postable.copy(delta = 1)))
        assertEquals(notToday, decide(postable.copy(delta = -1)))
        assertEquals(notToday, decide(postable.copy(delta = null)))
        assertEquals(notToday, decide(postable.copy(delta = 1, link = LinkState.Paused("me", Kind.Challenge))))
        assertEquals(notToday, decide(postable.copy(delta = 1, sending = true)))
    }

    @Test
    fun `a paused link blocks today's postable solve`() = runTest {
        assertEquals(
            Verdict.Blocked(LinkedInPostState.Paused),
            decide(postable.copy(link = LinkState.Paused("me", Kind.SessionExpired))),
        )
    }

    @Test
    fun `a leftover sending marker leaves the solve open but unsure`() = runTest {
        val verdict = decide(postable.copy(sending = true)) as Verdict.Open

        assertEquals(true, verdict.unsure)
    }

    @Test
    fun `a result LinkedIn is known to hold makes today's postable solve Already on LinkedIn, and a paused link or a missing replay still wins`() = runTest {
        val held = postable.copy(onLinkedIn = true)

        assertEquals(Verdict.Blocked(LinkedInPostState.AlreadyOnLinkedIn), decide(held))
        assertEquals(Verdict.Blocked(LinkedInPostState.Paused), decide(held.copy(link = LinkState.Paused("me", Kind.Challenge))))
        assertEquals(hidden, decide(held, encoded = null))
        assertEquals(Verdict.Blocked(LinkedInPostState.NotToday), decide(held.copy(delta = 1)))
        assertEquals(hidden, decide(held.copy(posting = false)))
    }
}
