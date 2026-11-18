package com.ivandsky.puzzlebox.domain

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.LegResult
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.MatchRepository
import com.ivandsky.puzzlebox.data.match.ResultPostState
import com.ivandsky.puzzlebox.data.match.ResultRead
import com.ivandsky.puzzlebox.data.match.SelfSeats
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeMatchLocalRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchResultOutbox
import com.ivandsky.puzzlebox.testing.repository.FakeSelfMatchRepository
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The duel read model of a self duel (me against my own first solves of two alpha dailies) over the device's self
 * store: I am the fixed self seat signed in or out, and a past solve stays hidden until my own result for that
 * puzzle is posted. [repo] counts the result reads.
 */
class ObserveMatchSelfTest {

    private val code = ShareCode.parse("K7Q2MX")!!
    private val created = Instant.ofEpochMilli(5_000_000)
    private val account = Identity("ann", "Ann", null)

    private val self = FakeSelfMatchRepository()
    private val reads = mutableListOf<Int>()
    private val repo = object : MatchRepository by self {
        override suspend fun readResult(code: ShareCode, legIndex: Int, uid: String): ResultRead {
            reads += legIndex
            return self.readResult(code, legIndex, uid)
        }
    }
    private val outbox = FakeMatchResultOutbox()
    private val identity = FakeIdentityService()
    private val registry = TestGames.registry(listOf(TestGames.alpha))

    private fun TestScope.observe(): Flow<MatchState> =
        ObserveMatch(repo, outbox, FakeMatchLocalRepository(), identity, registry) { created }(code)

    private fun day(index: Int) = LocalDate.of(2026, 11, 1 + index)

    /** Past me solved puzzle 1 in 41 s and puzzle 2 in 52 s. */
    private suspend fun aSelfDuelIsSaved() {
        val legs = List(2) { MatchLeg(LegRef.Daily(TestGames.alpha, day(it), it + 1), authorUid = null) }
        val records = List(2) {
            CompletionRecord(
                puzzleRef = TestGames.Ref(TestGames.alpha, PuzzleKey.Daily(day(it), it + 1)),
                time = 41_000L + it * 11_000L,
                hints = 0,
                stats = RecordStats(schemaVersion = 1, blob = "{}"),
                firstCompletedAt = Instant.ofEpochMilli(1_000),
                isRecord = true,
            )
        }
        val draft = buildSelfMatch(code, created, includeFriends = false, legs = legs, records = records)
        self.save(draft.match, draft.past)
    }

    private fun mine(leg: Int, timeMs: Long = 30_000) = LegResult(SelfSeats.ME, leg, timeMs, 0, 9_000, false)

    /** My result for [leg] as the poster leaves it once the self store took it. */
    private suspend fun iPosted(leg: Int, timeMs: Long = 30_000) {
        outbox.enqueue(code, mine(leg, timeMs))
        self.post(code, mine(leg, timeMs))
        outbox.update(code, leg, SelfSeats.ME, ResultPostState.Posted, attempts = 0)
    }

    private suspend fun ReceiveTurbine<MatchState>.view(): MatchView = (awaitItem() as MatchState.Loaded).view

    @Test
    fun aSignedOutPlayerSeesTheirSelfDuelFromTheSelfSeat() = runTest {
        aSelfDuelIsSaved()
        identity.currentIdentity = null

        observe().test {
            val view = view()
            assertEquals(SelfSeats.ME, view.me)
            assertEquals(SelfSeats.PAST_NAME, view.match.opponentOf(view.me)?.name)
            assertTrue(view.mine.isEmpty())
        }
    }

    @Test
    fun aSignedInPlayerSeesTheirSelfDuelFromTheSelfSeatNotTheAccount() = runTest {
        aSelfDuelIsSaved()
        identity.currentIdentity = account
        iPosted(leg = 0)

        observe().test {
            val view = view()
            assertEquals(SelfSeats.ME, view.me)
            assertEquals(listOf(0), view.mine.map { it.result.legIndex })
        }
    }

    @Test
    fun aPastSolveStaysHiddenUntilMyResultForThatPuzzleIsPosted() = runTest {
        aSelfDuelIsSaved()
        identity.currentIdentity = null

        observe().test {
            assertEquals(listOf(LegSeen.Played, LegSeen.Played), view().theirs)

            outbox.enqueue(code, mine(leg = 0))
            assertEquals("a queued result reveals nothing", listOf(LegSeen.Played, LegSeen.Played), view().theirs)
            assertTrue("no past result is read before mine is posted", reads.isEmpty())

            self.post(code, mine(leg = 0))
            outbox.update(code, 0, SelfSeats.ME, ResultPostState.Posted, attempts = 0)
            val revealed = expectMostRecentItem() as MatchState.Loaded
            assertEquals(
                listOf(LegSeen.Revealed(LegResult(SelfSeats.PAST, 0, 41_000, 0, 1_000, false)), LegSeen.Played),
                revealed.view.theirs,
            )
            assertEquals("only the puzzle I posted is read", setOf(0), reads.toSet())
        }
    }

    @Test
    fun aPostedSelfDuelPuzzleIsScoredAgainstThePastSolve() = runTest {
        aSelfDuelIsSaved()
        identity.currentIdentity = null
        iPosted(leg = 0, timeMs = 30_000)

        observe().test {
            val leg = view().outcome.legs[0]
            assertEquals("30 s beats past me's 41 s", LegVerdict.Win(LegBasis.Time), leg)
        }
    }

    @Test
    fun signingInOrOutKeepsTheSameSeatAndResults() = runTest {
        aSelfDuelIsSaved()
        identity.currentIdentity = null
        iPosted(leg = 1)

        observe().test {
            val signedOut = view()
            identity.currentIdentity = account
            val signedIn = view()

            assertEquals(signedOut, signedIn)
        }
    }

    @Test
    fun theSelfSeatWearsTheSignedInAccountsPhotoAndSignedOutHasNone() = runTest {
        aSelfDuelIsSaved()
        identity.currentIdentity = account.copy(photoUrl = "https://example.com/ann.png")

        observe().test {
            val signedIn = view()
            assertEquals("https://example.com/ann.png", signedIn.match.challenger.photoUrl)
            assertNull(signedIn.match.opponent?.photoUrl)
            identity.currentIdentity = null
            assertNull(view().match.challenger.photoUrl)
        }
    }
}
