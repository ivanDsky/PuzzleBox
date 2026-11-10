package com.ivandsky.puzzlebox.domain

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.LegResult
import com.ivandsky.puzzlebox.data.match.LocalRole
import com.ivandsky.puzzlebox.data.match.Match
import com.ivandsky.puzzlebox.data.match.MatchDraft
import com.ivandsky.puzzlebox.data.match.MatchFetch
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.MatchRepository
import com.ivandsky.puzzlebox.data.match.OutboxResult
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.data.match.ResultPostState
import com.ivandsky.puzzlebox.data.match.ResultPostState.LocalOnly
import com.ivandsky.puzzlebox.data.match.ResultPostState.Late
import com.ivandsky.puzzlebox.data.match.ResultPostState.Pending
import com.ivandsky.puzzlebox.data.match.ResultPostState.Posted
import com.ivandsky.puzzlebox.data.match.ResultRead
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeMatchLocalRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchResultOutbox
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The duel read model over the rules-faithful fakes; Ann challenges, Bob claims. The server clock stays at the epoch
 * and the use case reads the virtual scheduler, so every match is created at time 0. [repo] counts sealed reads and
 * lets a test script a read the rules cannot produce.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveMatchTest {

    private val code = ShareCode.parse("K7Q2MX")!!
    private val ann = Player("ann", "Ann", null)
    private val bob = Player("bob", "Bob", null)

    private val server = FakeMatchRepository()
    private val reads = mutableListOf<Int>()
    private val scriptedReads = mutableMapOf<Int, ResultRead>()
    private var scriptedFetch: Flow<MatchFetch>? = null
    private var subscriptions = 0
    private val repo = object : MatchRepository by server {
        override fun observe(code: ShareCode): Flow<MatchFetch> =
            (scriptedFetch ?: server.observe(code)).onStart { subscriptions++ }

        override suspend fun readResult(code: ShareCode, legIndex: Int, uid: String): ResultRead {
            reads += legIndex
            return scriptedReads[legIndex] ?: server.readResult(code, legIndex, uid)
        }
    }
    private val outbox = FakeMatchResultOutbox()
    private val local = FakeMatchLocalRepository()
    private val identity = FakeIdentityService()
    private val registry = TestGames.registry(listOf(TestGames.alpha, TestGames.beta))

    private val proto = match()
    private val deadlineMs = proto.deadline.toEpochMilli()
    private val closesAtMs = proto.closesAt.toEpochMilli()

    private fun TestScope.observe(): Flow<MatchState> =
        ObserveMatch(repo, outbox, local, identity, registry) { Instant.ofEpochMilli(testScheduler.currentTime) }(code)

    private fun viewAs(player: Player) {
        identity.currentIdentity = Identity(player.uid, player.name, null)
        server.signedInUid = player.uid
    }

    private fun legs(count: Int) = List(count) { index ->
        val game = if (index % 2 == 0) TestGames.alpha else TestGames.beta
        MatchLeg(LegRef.Daily(game, LocalDate.of(2026, 11, 1 + index), index + 1), authorUid = null)
    }

    /** Ann creates a duel of [legCount] dailies and, when [claimed], Bob takes the seat; the viewer is Ann after. */
    private suspend fun created(legCount: Int = 3, claimed: Boolean = true) {
        viewAs(ann)
        server.create(MatchDraft(code, 24, ann, invitedUid = null, legs(legCount), includeFriends = false, rematchOf = null))
        if (claimed) {
            server.signedInUid = bob.uid
            server.claim(code, bob)
        }
        viewAs(ann)
    }

    private fun result(uid: String, leg: Int, timeMs: Long = 60_000) = LegResult(uid, leg, timeMs, 0, 1_000, false)

    /** [uid]'s poster lands a result on the server: the result document plus the progress entry. */
    private suspend fun posts(uid: String, leg: Int, timeMs: Long = 60_000): LegResult {
        val viewer = server.signedInUid
        server.signedInUid = uid
        server.post(code, result(uid, leg, timeMs))
        server.signedInUid = viewer
        return result(uid, leg, timeMs)
    }

    /** The viewer's own outbox row for [leg] in [state]; a Posted row also lands on the server unless told not to. */
    private suspend fun myRow(leg: Int, state: ResultPostState, timeMs: Long = 60_000, onServer: Boolean = state == Posted) {
        val me = identity.currentIdentity!!.uid
        outbox.enqueue(code, result(me, leg, timeMs))
        outbox.update(code, leg, me, state, attempts = 1)
        if (onServer) posts(me, leg, timeMs)
    }

    private suspend fun ReceiveTurbine<MatchState>.view(): MatchView = (awaitItem() as MatchState.Loaded).view

    @Test
    fun fetchOutcomesMapToTheirStates() = runTest {
        viewAs(ann)
        observe().test {
            assertEquals(MatchState.NotFound, awaitItem())
            server.online = false
            assertEquals(MatchState.Offline, awaitItem())
        }
        scriptedFetch = flowOf(MatchFetch.UnsupportedVersion(2))
        observe().test {
            assertEquals(MatchState.UpdateApp(2), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aSignedOutViewerSeesTheChallengerGamesAndDeadline() = runTest {
        created()
        posts(bob.uid, 0)
        myRow(0, Posted)
        identity.currentIdentity = null
        observe().test {
            val state = awaitItem() as MatchState.SignedOut
            assertEquals(server.matches.getValue(code), state.match)
            assertTrue(reads.isEmpty())

            viewAs(ann)
            assertEquals(ann.uid, view().me)
        }
    }

    @Test
    fun aSignedOutViewerOfAMissingMatchSeesNotFound() = runTest {
        identity.currentIdentity = null
        observe().test {
            assertEquals(MatchState.NotFound, awaitItem())
        }
    }

    @Test
    fun aSignedOutViewerOfflineKeepsTheSignInCardWithoutDetails() = runTest {
        created()
        identity.currentIdentity = null
        server.online = false
        observe().test {
            assertEquals(MatchState.SignedOut(null), awaitItem())
        }
    }

    /** The rules let a seated reader read the opponent's results once the match has closed, posted or not. */
    private fun serverClosesTheMatch() {
        server.now = proto.closesAt
    }

    @Test
    fun anOpponentResultIsReadAtCloseWhenINeverPlayed() = runTest {
        created()
        val bobs = posts(bob.uid, 0)
        observe().test {
            assertEquals(listOf(LegSeen.Played, LegSeen.NotPlayed, LegSeen.NotPlayed), view().theirs)
            advanceTimeBy(closesAtMs - 1)
            expectNoEvents()
            assertTrue(reads.isEmpty())

            serverClosesTheMatch()
            advanceTimeBy(2)
            val closed = view()
            assertEquals(listOf(LegSeen.Revealed(bobs), LegSeen.NotPlayed, LegSeen.NotPlayed), closed.theirs)
            assertEquals(LegVerdict.Loss(LegBasis.Time), closed.outcome.legs[0])
            assertEquals(MatchVerdict.Lost, closed.outcome.verdict)
        }
        assertEquals(listOf(0), reads)
    }

    @Test
    fun anOpponentResultIsReadAtCloseWhenMyRowIsLateOrLocalOnlyOrPending() = runTest {
        created()
        val bobs = (0..2).map { posts(bob.uid, it) }
        myRow(0, Late)
        myRow(1, LocalOnly)
        myRow(2, Pending)
        observe().test {
            assertEquals(List(3) { LegSeen.Played }, view().theirs)
            assertTrue(reads.isEmpty())

            serverClosesTheMatch()
            advanceTimeBy(closesAtMs + 1)
            assertEquals(bobs.map { LegSeen.Revealed(it) }, view().theirs)
        }
    }

    @Test
    fun aLegReadBeforeCloseThatCameBackMissingOrOfflineIsReadAgainAtClose() = runTest {
        created(legCount = 2)
        val bobs = (0..1).map { posts(bob.uid, it) }
        myRow(0, Posted)
        myRow(1, Posted)
        scriptedReads[0] = ResultRead.Missing
        scriptedReads[1] = ResultRead.Offline
        observe().test {
            assertEquals(List(2) { LegSeen.Played }, view().theirs)
            scriptedReads.clear()
            advanceTimeBy(closesAtMs + 1)
            assertEquals(bobs.map { LegSeen.Revealed(it) }, view().theirs)
        }
    }

    @Test
    fun aRevealedLegIsNotReadAgainAtClose() = runTest {
        created(legCount = 1)
        val bobs = posts(bob.uid, 0)
        myRow(0, Posted)
        observe().test {
            assertEquals(listOf(LegSeen.Revealed(bobs)), view().theirs)
            advanceTimeBy(closesAtMs + 1)
            assertEquals(listOf(LegSeen.Revealed(bobs)), view().theirs)
        }
        assertEquals(listOf(0), reads)
    }

    @Test
    fun aReadThatIsOfflineAtCloseIsRetriedUntilTheResultArrives() = runTest {
        created(legCount = 1)
        val bobs = posts(bob.uid, 0)
        scriptedReads[0] = ResultRead.Offline
        observe().test {
            assertEquals(listOf(LegSeen.Played), view().theirs)
            advanceTimeBy(closesAtMs + 1)
            assertEquals(listOf(LegSeen.Played), view().theirs)

            scriptedReads.remove(0)
            serverClosesTheMatch()
            val retried = view()
            assertEquals(listOf(LegSeen.Revealed(bobs)), retried.theirs)
            assertEquals(MatchVerdict.Lost, retried.outcome.verdict)
            expectNoEvents()
        }
    }

    /** A device clock ahead of the server's close reads Sealed for a moment; it must not settle as a win. */
    @Test
    fun aSealedReadAtCloseIsRetriedUntilTheResultArrives() = runTest {
        created(legCount = 1)
        val bobs = posts(bob.uid, 0)
        scriptedReads[0] = ResultRead.Sealed
        observe().test {
            assertEquals(listOf(LegSeen.Played), view().theirs)
            advanceTimeBy(closesAtMs + 1)
            assertEquals(listOf(LegSeen.Played), view().theirs)

            scriptedReads.remove(0)
            serverClosesTheMatch()
            val retried = view()
            assertEquals(listOf(LegSeen.Revealed(bobs)), retried.theirs)
            assertEquals(LegVerdict.Loss(LegBasis.Time), retried.outcome.legs.single())
            assertEquals(MatchVerdict.Lost, retried.outcome.verdict)
            expectNoEvents()
        }
    }

    @Test
    fun aPendingRowStopsCountingOnceTheMatchHasClosedButAPostedOneStillDoes() = runTest {
        created(legCount = 2)
        posts(bob.uid, 0, timeMs = 60_000)
        posts(bob.uid, 1, timeMs = 60_000)
        myRow(0, Pending, timeMs = 30_000)
        myRow(1, Posted, timeMs = 30_000)
        observe().test {
            view()
            serverClosesTheMatch()
            advanceTimeBy(closesAtMs + 1)
            val closed = view()
            assertEquals(listOf(LegVerdict.Loss(LegBasis.Time), LegVerdict.Win(LegBasis.Time)), closed.outcome.legs)
            assertEquals(listOf(Pending, Posted), closed.mine.map { it.state })
        }
    }

    @Test
    fun aMissingResultAtCloseIsNotReadForever() = runTest {
        created(legCount = 1)
        posts(bob.uid, 0)
        scriptedReads[0] = ResultRead.Missing
        observe().test {
            view()
            advanceTimeBy(closesAtMs + 1)
            assertEquals(listOf(LegSeen.Played), view().theirs)
            val readsAtClose = reads.size
            advanceTimeBy(closesAtMs)
            expectNoEvents()
            assertEquals(readsAtClose, reads.size)
        }
    }

    @Test
    fun theirLegIsNotPlayedUntilTheirProgressNamesIt() = runTest {
        created(legCount = 2)
        observe().test {
            assertEquals(listOf(LegSeen.NotPlayed, LegSeen.NotPlayed), view().theirs)
            posts(bob.uid, 1)
            assertEquals(listOf(LegSeen.NotPlayed, LegSeen.Played), view().theirs)
        }
        viewAs(bob)
        observe().test {
            assertEquals(listOf(LegSeen.NotPlayed, LegSeen.NotPlayed), view().theirs)
            posts(ann.uid, 0)
            assertEquals(listOf(LegSeen.Played, LegSeen.NotPlayed), view().theirs)
        }
    }

    @Test
    fun aPlayedLegStaysSealedWhileMyResultIsNotPosted() = runTest {
        created()
        repeat(3) { posts(bob.uid, it) }
        myRow(0, Pending)
        myRow(1, Late)
        myRow(2, LocalOnly)
        observe().test {
            assertEquals(List(3) { LegSeen.Played }, view().theirs)
        }
        assertTrue(reads.isEmpty())
    }

    @Test
    fun aPostedResultOfMineRevealsTheirs() = runTest {
        created(legCount = 4)
        val bobs = (0..3).map { posts(bob.uid, it) }
        myRow(0, Posted)
        myRow(1, Posted, onServer = false)
        myRow(2, Posted)
        myRow(3, Posted)
        scriptedReads[2] = ResultRead.Missing
        scriptedReads[3] = ResultRead.Offline
        observe().test {
            assertEquals(
                listOf(LegSeen.Revealed(bobs[0]), LegSeen.Played, LegSeen.Played, LegSeen.Played),
                view().theirs,
            )
        }
        assertEquals(listOf(0, 1, 2, 3), reads.sorted())
    }

    @Test
    fun aRevealedLegIsReadOnceAcrossMatchUpdates() = runTest {
        created()
        val bobs0 = posts(bob.uid, 0)
        myRow(0, Posted)
        observe().test {
            assertEquals(LegSeen.Revealed(bobs0), view().theirs[0])
            posts(bob.uid, 1)
            val after = view()
            assertEquals(listOf(LegSeen.Revealed(bobs0), LegSeen.Played, LegSeen.NotPlayed), after.theirs)
        }
        assertEquals(listOf(0), reads)
    }

    @Test
    fun lateAndLocalOnlyRowsListedButNotCounted() = runTest {
        created()
        myRow(0, Late)
        myRow(1, LocalOnly)
        myRow(2, Pending)
        observe().test {
            assertEquals(listOf(Late, LocalOnly, Pending), view().mine.map { it.state })
            advanceTimeBy(closesAtMs + 1)
            val closed = view().outcome
            assertEquals(List(3) { LegVerdict.Draw }, closed.legs)
            assertEquals(0, closed.myWins)
        }
    }

    @Test
    fun theOutcomeIsComputedFromMyCountedRowsAndRevealedResults() = runTest {
        created(legCount = 2)
        val bobs = listOf(posts(bob.uid, 0, timeMs = 75_000), posts(bob.uid, 1, timeMs = 40_000))
        myRow(0, Posted, timeMs = 60_000)
        myRow(1, Posted, timeMs = 30_000)
        observe().test {
            val view = view()
            val anns = view.mine.map { it.result }
            val now = Instant.ofEpochMilli(testScheduler.currentTime)
            assertEquals(ComputeMatchOutcome(view.match, anns + bobs, ann.uid, now, registry::rankOrderOf), view.outcome)
            assertEquals(MatchVerdict.Won, view.outcome.verdict)
        }
    }

    /** The outbox is write-once per (uid, leg), so a second result for a leg never replaces the first. */
    @Test
    fun twoResultsForOneLegCountTheFirst() = runTest {
        created(legCount = 1)
        posts(bob.uid, 0, timeMs = 45_000)
        myRow(0, Posted, timeMs = 60_000)
        outbox.enqueue(code, result(ann.uid, 0, timeMs = 30_000))
        observe().test {
            val view = view()
            assertEquals(listOf(60_000L), view.mine.map { it.result.timeMs })
            assertEquals(listOf<LegVerdict>(LegVerdict.Loss(LegBasis.Time)), view.outcome.legs)
        }
    }

    @Test
    fun theLocalRowRidesTheView() = runTest {
        created()
        local.remember(ShareCode.parse("ABCDEF")!!, LocalRole.Participant, "Cat")
        observe().test {
            assertNull(view().local)
            local.remember(code, LocalRole.Participant, "Bob")
            val cached = local.observeAll().first().single { it.code == code }
            assertEquals(cached, view().local)
        }
    }

    @Test
    fun theViewSettlesAtClosesAtWithNoUpstreamChange() = runTest {
        created()
        observe().test {
            assertFalse(view().outcome.settled)
            advanceTimeBy(closesAtMs - 1)
            expectNoEvents()
            advanceTimeBy(2)
            assertTrue(view().outcome.settled)
        }
    }

    @Test
    fun anUnclaimedMatchReEmitsAtItsDeadline() = runTest {
        created(claimed = false)
        observe().test {
            assertFalse(view().outcome.settled)
            advanceTimeBy(deadlineMs - 1)
            expectNoEvents()
            advanceTimeBy(2)
            assertTrue(view().outcome.settled)
            advanceTimeBy(closesAtMs - deadlineMs - 2)
            expectNoEvents()
            advanceTimeBy(2)
            view()
            advanceTimeBy(closesAtMs)
            expectNoEvents()
        }
    }

    private fun match(
        opponent: Player? = bob,
        cancelled: Boolean = false,
        declined: Boolean = false,
        bobsProgress: Set<Int> = emptySet(),
    ) = Match(
        code = code,
        formatVersion = Match.FORMAT_VERSION,
        createdAt = Instant.EPOCH,
        deadlineHours = 24,
        challenger = ann,
        invitedUid = bob.uid,
        opponent = opponent,
        legs = legs(2),
        includeFriends = false,
        cancelled = cancelled,
        declined = declined,
        rematchOf = null,
        progress = mapOf(bob.uid to bobsProgress),
    )

    private fun row(leg: Int, state: ResultPostState) = OutboxResult(code, result(ann.uid, leg), state, attempts = 1)

    @Test
    fun revealIsReadyWhenMyRowsAreLateOrLocalOnlyAndTheOpponentFinished() {
        val match = match(bobsProgress = setOf(0, 1))
        assertTrue(match.revealReady(ann.uid, listOf(row(0, Late), row(1, LocalOnly)), Instant.EPOCH))
    }

    @Test
    fun revealIsNotReadyWhileMyPostIsPendingEvenIfTheOpponentIsAhead() {
        val match = match(bobsProgress = setOf(0, 1))
        assertFalse(match.revealReady(ann.uid, listOf(row(0, Posted), row(1, Pending)), Instant.EPOCH))
    }

    @Test
    fun revealIsReadyOnceTheMatchIsClosed() {
        assertTrue(match().revealReady(ann.uid, emptyList(), proto.closesAt))
    }

    @Test
    fun revealIsNeverReadyForACancelledDeclinedOrUnclaimedMatch() {
        val done = listOf(row(0, Posted), row(1, Posted))
        for (match in listOf(match(cancelled = true), match(declined = true), match(opponent = null))) {
            val both = match.copy(progress = mapOf(bob.uid to setOf(0, 1)))
            assertFalse(both.revealReady(ann.uid, done, both.closesAt))
            assertFalse(both.revealReady(ann.uid, done, Instant.EPOCH))
        }
    }
}
