package com.ivandsky.puzzlebox.data.linkedin

import com.ivandsky.puzzlebox.datastore.LinkedInLinkPrefs
import com.ivandsky.puzzlebox.game.linkedin.LinkedInAccountSession
import com.ivandsky.puzzlebox.game.linkedin.LinkedInAccountSource
import com.ivandsky.puzzlebox.game.linkedin.LinkedInOwnResult
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead.Rejected.Kind
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSendResult
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSessionCookies
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A post that outlives its link leaves nothing in what the link is by then: no posted ref, no marker, no pause. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LinkedInPostFlowDisconnectTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val rigs = mutableListOf<LinkedInPostRig>()

    @After
    fun tearDown() {
        rigs.forEach { it.close() }
    }

    private fun TestScope.rig(cookies: LinkedInSessionCookies? = null): LinkedInPostRig {
        val rig = if (cookies == null) LinkedInPostRig(this, folder.newFolder()) else LinkedInPostRig(this, folder.newFolder(), cookies)
        return rig.also { rigs += it }
    }

    /** The tap's result and the link file after [relink] ran while the send was on the wire and [reply] came after it. */
    private suspend fun TestScope.answeredAfter(
        reply: LinkedInSendResult,
        relink: suspend LinkedInLink.() -> Unit,
    ): Pair<LinkedInPostState, LinkedInLinkPrefs> {
        val rig = rig()
        rig.postable()
        val wire = Wire()
        rig.poster.sendReply = reply
        rig.poster.onSend = { wire.hold() }

        val tap = async { rig.submit() }
        wire.reached.await()
        rig.link.relink()
        wire.answer.complete(Unit)
        return tap.await() to rig.stored()
    }

    @Test
    fun `a disconnect while the post is on the wire leaves nothing of it, whatever LinkedIn answers`() = runTest(timeout = 10.seconds) {
        val replies = listOf(
            LinkedInSendResult.Posted,
            LinkedInSendResult.AlreadyPlayed,
            LinkedInSendResult.Rejected(Kind.Challenge),
            LinkedInSendResult.SignedOut,
            LinkedInSendResult.Offline,
            LinkedInSendResult.Unknown,
        )

        val seen = replies.map { reply -> answeredAfter(reply) { disconnect() } }

        assertEquals(List(6) { LinkedInPostState.Hidden to LinkedInLinkPrefs(null, null, null, null) }, seen)
    }

    @Test
    fun `an answer that comes after another account was linked does not touch that account`() = runTest(timeout = 10.seconds) {
        val other: suspend LinkedInLink.() -> Unit = {
            disconnect()
            connect("other", 3_000L)
        }

        val seen = listOf(answeredAfter(LinkedInSendResult.Rejected(Kind.Challenge), other), answeredAfter(LinkedInSendResult.Posted, other))

        assertEquals(List(2) { LinkedInPostState.Hidden to LinkedInLinkPrefs("other", 3_000L, null, null) }, seen)
    }

    @Test
    fun `an answer that comes after the same account was linked anew is not remembered in the new link`() = runTest(timeout = 10.seconds) {
        val again: suspend LinkedInLink.() -> Unit = {
            disconnect()
            connect("me", 3_000L)
        }

        val seen = listOf(answeredAfter(LinkedInSendResult.Rejected(Kind.Challenge), again), answeredAfter(LinkedInSendResult.Posted, again))

        assertEquals(List(2) { LinkedInPostState.Hidden to LinkedInLinkPrefs("me", 3_000L, null, null) }, seen)
    }

    @Test
    fun `a check LinkedIn rejects after another account was linked does not pause that account`() = runTest(timeout = 10.seconds) {
        val rig = rig()
        rig.postable()
        rig.source.respondOwn("me", 6, 712, LinkedInRead.Rejected(Kind.Challenge))
        val wire = Wire()
        val door = rig.door(source = HeldLinkedInAccountSource(rig.source, wire))

        val tap = async { door.submit(rig.game, rig.today) }
        wire.reached.await()
        rig.link.disconnect()
        rig.link.connect("other", 3_000L)
        wire.answer.complete(Unit)

        assertEquals(LinkedInPostState.Hidden, tap.await())
        assertEquals(LinkedInLinkPrefs("other", 3_000L, null, null), rig.stored())
    }

    @Test
    fun `a post that reaches its send while a disconnect is wiping waits for the wipe and then sends nothing`() = runTest(timeout = 10.seconds) {
        val wiping = CompletableDeferred<Unit>()
        val finishWipe = CompletableDeferred<Unit>()
        val rig = rig(
            object : LinkedInSessionCookies {
                override suspend fun current(): LinkedInAccountSession? = null
                override suspend fun clear() {
                    wiping.complete(Unit)
                    finishWipe.await()
                }
            },
        )
        rig.postable()
        val disconnect = launch { rig.link.disconnect() }
        wiping.await()

        // The link still reads as connected, so the post passes every check but the lock.
        val tap = async { rig.submit() }
        advanceUntilIdle()
        finishWipe.complete(Unit)
        disconnect.join()

        assertEquals(LinkedInPostState.Hidden, tap.await())
        assertEquals(listOf("page:games/fixture/"), rig.poster.calls)
        assertEquals(LinkedInLinkPrefs(null, null, null, null), rig.stored())
    }

    /** Where a test holds a request: [reached] completes when the request is out, and it answers after [answer]. */
    private class Wire {
        val reached = CompletableDeferred<Unit>()
        val answer = CompletableDeferred<Unit>()

        suspend fun hold() {
            reached.complete(Unit)
            answer.await()
        }
    }

    private class HeldLinkedInAccountSource(
        private val inner: LinkedInAccountSource,
        private val wire: Wire,
    ) : LinkedInAccountSource by inner {
        override suspend fun ownResult(profileId: String, gameTypeId: Int, puzzleNumber: Int): LinkedInRead<LinkedInOwnResult> {
            wire.hold()
            return inner.ownResult(profileId, gameTypeId, puzzleNumber)
        }
    }
}
