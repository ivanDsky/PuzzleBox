package com.ivandsky.puzzlebox.data.match

import android.app.Application
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.network.FakeNetworkStatus
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchResultOutbox
import com.ivandsky.puzzlebox.testing.repository.FakeSelfMatchRepository
import java.time.Instant
import java.time.LocalDate
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
 * Whose rows the poster takes, over the app's routing repository: a self duel's rows post to the device's self
 * store with nobody signed in, and a friend duel's rows still wait for their own account. Robolectric: a refused
 * post logs through android.util.Log.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MatchResultPosterSelfTest {

    private val selfCode = ShareCode.parse("K7Q2MX")!!
    private val friendCode = ShareCode.parse("M8T3VQ")!!
    private val ann = Identity("u1", "Ann", null)
    private val legs = (1..2).map { MatchLeg(LegRef.Daily(GameId("zip"), LocalDate.of(2026, 11, it), it), authorUid = null) }

    private val identity = FakeIdentityService(initial = null)
    private val outbox = FakeMatchResultOutbox()
    private val self = FakeSelfMatchRepository()
    private val server = FakeMatchRepository()
    private val scheduler = TestCoroutineScheduler()
    private val process = CoroutineScope(StandardTestDispatcher(scheduler) + Job())
    private val poster = MatchResultPoster(identity, outbox, RoutingMatchRepository(self, server), FakeNetworkStatus(), process)

    @After
    fun stopProcess() {
        process.cancel()
    }

    private fun appStart() {
        poster.start()
        scheduler.advanceUntilIdle()
    }

    private fun signIn(who: Identity?) {
        identity.currentIdentity = who
        server.signedInUid = who?.uid
        scheduler.advanceUntilIdle()
    }

    private suspend fun aSelfDuelIsSaved() {
        val match = Match(
            code = selfCode,
            formatVersion = Match.FORMAT_VERSION,
            createdAt = Instant.ofEpochMilli(5_000_000),
            deadlineHours = SelfSeats.NO_DEADLINE_HOURS,
            challenger = Player(SelfSeats.ME, "You", photoUrl = null),
            invitedUid = null,
            opponent = Player(SelfSeats.PAST, SelfSeats.PAST_NAME, photoUrl = null),
            legs = legs,
            includeFriends = false,
            cancelled = false,
            declined = false,
            rematchOf = null,
            progress = emptyMap(),
        )
        self.save(match, legs.indices.map { LegResult(SelfSeats.PAST, it, 41_000, 0, 1_000, gaveUp = false) })
    }

    /** Ann's open friend duel on the server; nobody is signed in after. */
    private suspend fun annsFriendDuelIsOnTheServer() {
        server.signedInUid = ann.uid
        val draft = MatchDraft(friendCode, 24, Player(ann.uid, ann.displayName, null), invitedUid = null, legs, includeFriends = false, rematchOf = null)
        assertEquals(CreateResult.Created(friendCode), server.create(draft))
        server.signedInUid = null
    }

    private fun result(uid: String, legIndex: Int = 0) =
        LegResult(uid, legIndex, timeMs = 30_000, hints = 0, completedAtMs = 9_000, gaveUp = false)

    private suspend fun row(code: ShareCode, uid: String, legIndex: Int = 0): OutboxResult =
        outbox.observeAll().first().single { it.code == code && it.result.uid == uid && it.result.legIndex == legIndex }

    private suspend fun mineInTheSelfStore(legIndex: Int = 0): ResultRead = self.readResult(selfCode, legIndex, SelfSeats.ME)

    @Test
    fun aSelfDuelRowPostsToTheDeviceStoreSignedOut() = runTest {
        aSelfDuelIsSaved()
        outbox.enqueue(selfCode, result(SelfSeats.ME))

        appStart()

        assertEquals(OutboxResult(selfCode, result(SelfSeats.ME), ResultPostState.Posted, 0), row(selfCode, SelfSeats.ME))
        assertEquals(ResultRead.Found(result(SelfSeats.ME)), mineInTheSelfStore())
    }

    @Test
    fun aSelfDuelRowQueuedWhileTheAppRunsSignedOutIsPosted() = runTest {
        aSelfDuelIsSaved()
        appStart()

        outbox.enqueue(selfCode, result(SelfSeats.ME, legIndex = 1))
        scheduler.advanceUntilIdle()

        assertEquals(ResultPostState.Posted, row(selfCode, SelfSeats.ME, legIndex = 1).state)
        assertEquals(ResultRead.Found(result(SelfSeats.ME, legIndex = 1)), mineInTheSelfStore(legIndex = 1))
    }

    @Test
    fun aSelfDuelRowPostsSignedInToo() = runTest {
        aSelfDuelIsSaved()
        signIn(ann)
        outbox.enqueue(selfCode, result(SelfSeats.ME))

        appStart()

        assertEquals(ResultPostState.Posted, row(selfCode, SelfSeats.ME).state)
        assertEquals(ResultRead.Found(result(SelfSeats.ME)), mineInTheSelfStore())
    }

    @Test
    fun aFriendDuelRowStillWaitsSignedOutBesideAPostedSelfRow() = runTest {
        aSelfDuelIsSaved()
        annsFriendDuelIsOnTheServer()
        outbox.enqueue(selfCode, result(SelfSeats.ME))
        outbox.enqueue(friendCode, result(ann.uid))

        appStart()

        assertEquals(ResultPostState.Posted, row(selfCode, SelfSeats.ME).state)
        assertEquals(OutboxResult(friendCode, result(ann.uid), ResultPostState.Pending, 0), row(friendCode, ann.uid))
        assertNull(server.matches.getValue(friendCode).progress[ann.uid])

        signIn(ann)

        assertEquals("the account's own row posts once it signs in", ResultPostState.Posted, row(friendCode, ann.uid).state)
    }
}
