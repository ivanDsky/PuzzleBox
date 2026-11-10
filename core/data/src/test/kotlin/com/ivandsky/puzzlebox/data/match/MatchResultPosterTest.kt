package com.ivandsky.puzzlebox.data.match

import android.app.Application
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.network.FakeNetworkStatus
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchResultOutbox
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Posting matrix of the duel outbox: each server outcome lands on the row, only the signed-in owner's rows post, a
 * row is tried once per process until poked, and failures that are not the server's verdict stay Pending. The
 * repository is the in-memory one with the rules' semantics, so Late and Duplicate come from the server side.
 * Robolectric: deferred posts log through android.util.Log.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MatchResultPosterTest {

    private val code = ShareCode.parse("K7Q2MX")!!
    private val unknownMatch = ShareCode.parse("ZZZ999")!!
    private val ann = Identity("u1", "Ann", null)
    private val bob = Identity("u2", "Bob", null)

    private val identity = FakeIdentityService(initial = ann)
    private val outbox = FakeMatchResultOutbox()
    private val server = FakeMatchRepository().apply { signedInUid = ann.uid }
    private val matches = ScriptedMatchRepository(server)
    private val scheduler = TestCoroutineScheduler()
    private val network = FakeNetworkStatus()
    private var process: CoroutineScope? = null
    private lateinit var poster: MatchResultPoster

    /** Ann's open 24-hour match of three legs, which Bob has claimed; Ann stays signed in on both sides. */
    private suspend fun givenAClaimedMatch() {
        val legs = (1..3).map { MatchLeg(LegRef.Daily(GameId("zip"), LocalDate.of(2026, 11, 1), it), authorUid = null) }
        val draft = MatchDraft(code, 24, ann.asPlayer(), invitedUid = null, legs, includeFriends = false, rematchOf = null)
        assertEquals(CreateResult.Created(code), server.create(draft))
        server.signedInUid = bob.uid
        assertEquals(ClaimResult.Claimed, server.claim(code, bob.asPlayer()))
        server.signedInUid = ann.uid
    }

    private fun result(uid: String = ann.uid, legIndex: Int = 0) =
        LegResult(uid, legIndex, timeMs = 80_000, hints = 1, completedAtMs = 90_000, gaveUp = false)

    /** Both the app identity and the server's auth follow one account, as in production. */
    private fun signIn(who: Identity?) {
        identity.currentIdentity = who
        server.signedInUid = who?.uid
    }

    /** One app process: the previous one dies, a fresh poster starts on a fresh scope, run until idle. */
    private fun appStart() {
        process?.cancel()
        val scope = CoroutineScope(StandardTestDispatcher(scheduler) + Job())
        process = scope
        poster = MatchResultPoster(identity = identity, outbox = outbox, matches = matches, network = network, scope = scope)
        poster.start()
        scheduler.advanceUntilIdle()
    }

    /** Something the observer reacts to that is not one of the rows under test: a profile name change. */
    private fun unrelatedChange() {
        identity.currentIdentity = identity.currentIdentity?.let { it.copy(displayName = it.displayName + "!") }
        scheduler.advanceUntilIdle()
    }

    @After
    fun stopProcess() {
        process?.cancel()
    }

    private suspend fun row(legIndex: Int = 0, uid: String = ann.uid, match: ShareCode = code): OutboxResult =
        outbox.observeAll().first().single { it.code == match && it.result.legIndex == legIndex && it.result.uid == uid }

    @Test
    fun postedAndDuplicateResultsMarkTheRowPosted() = runTest {
        givenAClaimedMatch()
        assertEquals(PostResult.Posted, server.post(code, result(legIndex = 1)))
        outbox.enqueue(code, result(legIndex = 0))
        outbox.enqueue(code, result(legIndex = 1))

        appStart()

        assertEquals(OutboxResult(code, result(legIndex = 0), ResultPostState.Posted, 0), row(0))
        assertEquals(OutboxResult(code, result(legIndex = 1), ResultPostState.Posted, 0), row(1))
        assertEquals(setOf(0, 1), server.matches.getValue(code).progress[ann.uid])
    }

    @Test
    fun lateResultMarksTheRowLate() = runTest {
        givenAClaimedMatch()
        server.now = server.matches.getValue(code).closesAt
        outbox.enqueue(code, result())

        appStart()

        assertEquals(ResultPostState.Late, row().state)
        assertEquals(0, row().attempts)
        assertNull(server.matches.getValue(code).progress[ann.uid])
    }

    @Test
    fun rejectedResultCountsAnAttemptAndStaysPending() = runTest {
        outbox.enqueue(unknownMatch, result())

        appStart()

        assertEquals(1, matches.posts)
        assertEquals(ResultPostState.Pending, row(match = unknownMatch).state)
        assertEquals(1, row(match = unknownMatch).attempts)
    }

    @Test
    fun thirdRejectionBecomesLocalOnly() = runTest {
        outbox.enqueue(unknownMatch, result())

        appStart()
        appStart()
        assertEquals(ResultPostState.Pending, row(match = unknownMatch).state)
        appStart()
        appStart()

        assertEquals(3, matches.posts)
        assertEquals(ResultPostState.LocalOnly, row(match = unknownMatch).state)
        assertEquals(3, row(match = unknownMatch).attempts)
    }

    @Test
    fun unavailableLeavesTheRowPendingAndRetriesOnNextChange() = runTest {
        givenAClaimedMatch()
        server.online = false
        outbox.enqueue(code, result(legIndex = 0))

        appStart()
        assertEquals(ResultPostState.Pending, row(0).state)
        assertEquals(0, row(0).attempts)

        server.online = true
        outbox.enqueue(code, result(legIndex = 1))
        scheduler.advanceUntilIdle()

        assertEquals(ResultPostState.Posted, row(0).state)
        assertEquals(0, row(0).attempts)
        assertEquals(ResultPostState.Posted, row(1).state)
    }

    @Test
    fun anExceptionLeavesTheRowPendingAndRetriesOnNextChange() = runTest {
        givenAClaimedMatch()
        matches.failNextPost(IllegalStateException("socket closed"))
        outbox.enqueue(code, result())

        appStart()
        assertEquals(ResultPostState.Pending, row().state)
        assertEquals(0, row().attempts)

        unrelatedChange()

        assertEquals(2, matches.posts)
        assertEquals(ResultPostState.Posted, row().state)
    }

    @Test
    fun rowsOfAnotherUidAreNotPosted() = runTest {
        givenAClaimedMatch()
        outbox.enqueue(code, result(uid = bob.uid))

        appStart()

        assertEquals(0, matches.posts)
        assertEquals(ResultPostState.Pending, row(uid = bob.uid).state)

        signIn(bob)
        scheduler.advanceUntilIdle()

        assertEquals(ResultPostState.Posted, row(uid = bob.uid).state)
        assertEquals(setOf(0), server.matches.getValue(code).progress[bob.uid])
    }

    @Test
    fun signedOutPostsNothing() = runTest {
        givenAClaimedMatch()
        signIn(null)
        outbox.enqueue(code, result())

        appStart()

        assertEquals(0, matches.posts)
        assertEquals(ResultPostState.Pending, row().state)
        assertEquals(0, row().attempts)
    }

    @Test
    fun aRowIsTriedOncePerProcessUntilPoked() = runTest {
        outbox.enqueue(unknownMatch, result())

        appStart()
        unrelatedChange()
        outbox.enqueue(unknownMatch, result(legIndex = 1))
        scheduler.advanceUntilIdle()

        assertEquals(1, row(0, match = unknownMatch).attempts)
        assertEquals(2, matches.posts)

        poster.poke()
        scheduler.advanceUntilIdle()

        assertEquals(2, row(0, match = unknownMatch).attempts)
        assertEquals(2, row(1, match = unknownMatch).attempts)
        assertEquals(4, matches.posts)
    }

    @Test
    fun pokeRetriesAnUnavailableRowNow() = runTest {
        givenAClaimedMatch()
        server.online = false
        outbox.enqueue(code, result())
        appStart()

        server.online = true
        scheduler.advanceUntilIdle()
        assertEquals(ResultPostState.Pending, row().state)

        poster.poke()
        scheduler.advanceUntilIdle()

        assertEquals(ResultPostState.Posted, row().state)
        assertEquals(0, row().attempts)
    }

    @Test
    fun cancellationIsNotSwallowed() = runTest {
        givenAClaimedMatch()
        matches.failNextPost(CancellationException("post cancelled"))
        outbox.enqueue(code, result(legIndex = 0))

        appStart()
        assertEquals(ResultPostState.Pending, row(0).state)
        assertEquals(0, row(0).attempts)

        // A cancelled post is neither a server verdict nor a failure to retry on the next change; the poster lives on.
        outbox.enqueue(code, result(legIndex = 1))
        scheduler.advanceUntilIdle()

        assertEquals(2, matches.posts)
        assertEquals(ResultPostState.Pending, row(0).state)
        assertEquals(ResultPostState.Posted, row(1).state)

        poster.poke()
        scheduler.advanceUntilIdle()

        assertEquals(ResultPostState.Posted, row(0).state)
    }

    @Test
    fun aPokeDuringAPostNeverStartsASecondPost() = runTest {
        matches.holdPosts()
        outbox.enqueue(unknownMatch, result())
        appStart()

        poster.poke()
        scheduler.advanceUntilIdle()
        matches.release()
        scheduler.advanceUntilIdle()

        assertEquals(1, matches.posts)
        assertEquals(1, row(match = unknownMatch).attempts)

        poster.poke()
        scheduler.advanceUntilIdle()

        assertEquals(2, matches.posts)
        assertEquals(2, row(match = unknownMatch).attempts)
    }

    @Test
    fun aRowDeferredAsUnavailableIsPostedWhenConnectivityReturns() = runTest {
        givenAClaimedMatch()
        server.online = false
        network.setOnline(false)
        outbox.enqueue(code, result())
        appStart()
        assertEquals(ResultPostState.Pending, row().state)

        server.online = true
        network.setOnline(true)
        scheduler.advanceUntilIdle()

        assertEquals(ResultPostState.Posted, row().state)
        assertEquals(0, row().attempts)
    }

    @Test
    fun aRegainedConnectionDoesNotRepostARowAlreadyInFlight() = runTest {
        matches.holdPosts()
        outbox.enqueue(unknownMatch, result())
        appStart()

        network.setOnline(false)
        scheduler.advanceUntilIdle()
        network.setOnline(true)
        scheduler.advanceUntilIdle()
        matches.release()
        scheduler.advanceUntilIdle()

        assertEquals(1, matches.posts)
        assertEquals(1, row(match = unknownMatch).attempts)
    }

    @Test
    fun aRowAtThreeRejectionsStaysLocalOnlyAcrossAReconnect() = runTest {
        outbox.enqueue(unknownMatch, result())
        repeat(3) { appStart() }
        assertEquals(ResultPostState.LocalOnly, row(match = unknownMatch).state)

        network.setOnline(false)
        scheduler.advanceUntilIdle()
        network.setOnline(true)
        scheduler.advanceUntilIdle()

        assertEquals(3, matches.posts)
        assertEquals(ResultPostState.LocalOnly, row(match = unknownMatch).state)
        assertEquals(3, row(match = unknownMatch).attempts)
    }

    private fun Identity.asPlayer() = Player(uid, displayName, photoUrl)
}

/** Counts every post and can make the next one throw or wait, so a test can prove which attempts were made. */
private class ScriptedMatchRepository(private val server: FakeMatchRepository) : MatchRepository by server {
    var posts = 0
        private set
    private var failure: Throwable? = null
    private var gate: CompletableDeferred<Unit>? = null

    fun failNextPost(error: Throwable) {
        failure = error
    }

    /** Posts wait until [release]; the call is counted as soon as it starts. */
    fun holdPosts() {
        gate = CompletableDeferred()
    }

    fun release() {
        gate?.complete(Unit)
        gate = null
    }

    override suspend fun post(code: ShareCode, result: LegResult): PostResult {
        posts++
        failure?.let {
            failure = null
            throw it
        }
        gate?.await()
        return server.post(code, result)
    }
}
