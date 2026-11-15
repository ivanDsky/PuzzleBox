package com.ivandsky.puzzlebox.data.linkedin

import com.ivandsky.puzzlebox.game.linkedin.LinkedInAccountSource
import com.ivandsky.puzzlebox.game.linkedin.LinkedInGamePage
import com.ivandsky.puzzlebox.game.linkedin.LinkedInOwnResult
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead.Rejected.Kind
import com.ivandsky.puzzlebox.game.linkedin.LinkedInResultPoster
import com.ivandsky.puzzlebox.game.linkedin.LinkedInSendResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** One submit, step by step: what it asks LinkedIn, when it sends, and what each answer leaves behind. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LinkedInPostFlowTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val rigs = mutableListOf<LinkedInPostRig>()

    @After
    fun tearDown() {
        rigs.forEach { it.close() }
    }

    private fun TestScope.rig(callLimit: Int = 100): LinkedInPostRig =
        LinkedInPostRig(this, folder.newFolder(), callLimit = callLimit).also { rigs += it }

    /** What one submit ended as and left on the phone. */
    private data class Left(
        val result: LinkedInPostState,
        val sends: Int,
        val link: LinkState,
        val posted: Set<String>,
        val sending: Set<String>,
    )

    private suspend fun TestScope.submitAfter(script: LinkedInPostRig.() -> Unit): Left {
        val rig = rig()
        rig.postable()
        rig.script()
        val result = rig.submit()
        val stored = rig.stored()
        return Left(result, rig.sends, rig.link.state.first(), stored.postedRefs, stored.sendingRefs)
    }

    @Test
    fun `a day LinkedIn has no result for is checked, read and sent once, and remembered as posted`() = runTest {
        val rig = rig()
        rig.postable()
        rig.logOwnRead()

        val result = rig.submit()

        assertEquals(LinkedInPostState.Posted, result)
        assertEquals(listOf("own:me:6:712"), rig.source.calls)
        assertEquals(listOf("page:games/fixture/", "send"), rig.poster.calls)
        assertEquals(listOf(LinkedInPostRig.POST to LinkedInPostRig.PAGE), rig.poster.sent)
        assertEquals(setOf("fixture/2026-11-05"), rig.stored().postedRefs)
        assertEquals(emptySet<String>(), rig.stored().sendingRefs)
        assertNull(rig.ownRead())
    }

    @Test
    fun `nothing is sent or logged unless the check says not played and the page is this player's open puzzle`() = runTest {
        val me = LinkState.Connected("me")
        fun own(reply: LinkedInRead<LinkedInOwnResult>): LinkedInPostRig.() -> Unit = { source.respondOwn("me", 6, 712, reply) }
        fun page(reply: LinkedInRead<LinkedInGamePage>): LinkedInPostRig.() -> Unit = { poster.pageReply = reply }
        fun pageWith(change: LinkedInGamePage.() -> LinkedInGamePage) = page(LinkedInRead.Ok(LinkedInPostRig.PAGE.change()))
        val cases = mapOf(
            "check: solved" to own(LinkedInRead.Ok(LinkedInOwnResult.Solved(timeSeconds = 70, hintFree = true))),
            "check: offline" to own(LinkedInRead.Offline),
            "check: signed out" to own(LinkedInRead.SignedOut),
            "check: challenged" to own(LinkedInRead.Rejected(Kind.Challenge)),
            "page: offline" to page(LinkedInRead.Offline),
            "page: signed out" to page(LinkedInRead.SignedOut),
            "page: rate limited" to page(LinkedInRead.Rejected(Kind.RateLimited)),
            "page: another member's" to pageWith { copy(profileId = "someone-else") },
            "page: another puzzle" to pageWith { copy(puzzleId = 713) },
            "page: already won" to pageWith { copy(hasWon = true) },
            "page: the tutorial" to pageWith { copy(isTutorial = true) },
        )

        val left = cases.mapValues { (_, script) -> submitAfter(script) }

        fun nothingSent(result: LinkedInPostState, link: LinkState) = Left(result, sends = 0, link, emptySet(), emptySet())
        assertEquals(
            mapOf(
                "check: solved" to nothingSent(LinkedInPostState.AlreadyOnLinkedIn, me),
                "check: offline" to nothingSent(LinkedInPostState.Offline, me),
                "check: signed out" to nothingSent(LinkedInPostState.Paused, LinkState.Paused("me", Kind.SessionExpired)),
                "check: challenged" to nothingSent(LinkedInPostState.Paused, LinkState.Paused("me", Kind.Challenge)),
                "page: offline" to nothingSent(LinkedInPostState.Offline, me),
                "page: signed out" to nothingSent(LinkedInPostState.Paused, LinkState.Paused("me", Kind.SessionExpired)),
                "page: rate limited" to nothingSent(LinkedInPostState.Paused, LinkState.Paused("me", Kind.RateLimited)),
                "page: another member's" to nothingSent(LinkedInPostState.Paused, LinkState.Paused("me", Kind.Unexpected)),
                "page: another puzzle" to nothingSent(LinkedInPostState.NotToday, me),
                "page: already won" to nothingSent(LinkedInPostState.AlreadyOnLinkedIn, me),
                "page: the tutorial" to nothingSent(LinkedInPostState.Paused, LinkState.Paused("me", Kind.Unexpected)),
            ),
            left,
        )
    }

    @Test
    fun `each answer to the send is remembered its own way, and only a rejection or a lost session pauses`() = runTest {
        val me = LinkState.Connected("me")
        val ref = setOf("fixture/2026-11-05")
        val cases = mapOf(
            "posted" to LinkedInSendResult.Posted,
            "already played" to LinkedInSendResult.AlreadyPlayed,
            "rejected" to LinkedInSendResult.Rejected(Kind.Unexpected),
            "signed out" to LinkedInSendResult.SignedOut,
            "offline" to LinkedInSendResult.Offline,
            "no answer" to LinkedInSendResult.Unknown,
        )

        val left = cases.mapValues { (_, reply) -> submitAfter { poster.sendReply = reply } }

        assertEquals(
            mapOf(
                "posted" to Left(LinkedInPostState.Posted, 1, me, posted = ref, sending = emptySet()),
                "already played" to Left(LinkedInPostState.AlreadyOnLinkedIn, 1, me, posted = ref, sending = emptySet()),
                "rejected" to
                    Left(LinkedInPostState.Paused, 1, LinkState.Paused("me", Kind.Unexpected), emptySet(), emptySet()),
                "signed out" to
                    Left(LinkedInPostState.Paused, 1, LinkState.Paused("me", Kind.SessionExpired), emptySet(), emptySet()),
                "offline" to Left(LinkedInPostState.Offline, 1, me, posted = emptySet(), sending = emptySet()),
                "no answer" to Left(LinkedInPostState.Unknown, 1, me, posted = emptySet(), sending = ref),
            ),
            left,
        )
    }

    @Test
    fun `the sending marker is on disk before the post goes out`() = runTest {
        val rig = rig()
        rig.postable()
        var markedAtSend: Set<String>? = null
        rig.poster.onSend = { markedAtSend = rig.stored().sendingRefs }

        rig.submit()

        assertEquals(setOf("fixture/2026-11-05"), markedAtSend)
    }

    @Test
    fun `after a send with no answer the next tap asks LinkedIn first, and a result found there ends it unsent`() = runTest {
        val rig = rig()
        rig.postable()
        rig.logOwnRead()
        rig.poster.sendReply = LinkedInSendResult.Unknown
        rig.submit()
        rig.source.respondOwn("me", 6, 712, LinkedInRead.Ok(LinkedInOwnResult.Solved(timeSeconds = 83, hintFree = true)))

        val second = rig.submit()

        assertEquals(LinkedInPostState.AlreadyOnLinkedIn, second)
        assertEquals(listOf("own:me:6:712", "own:me:6:712"), rig.source.calls)
        assertEquals(listOf("page:games/fixture/", "send"), rig.poster.calls)
        assertEquals(emptySet<String>(), rig.stored().sendingRefs)
        assertEquals(emptySet<String>(), rig.stored().postedRefs)
        assertNull(rig.ownRead())
    }

    @Test
    fun `after a send with no answer a day LinkedIn still has no result for is sent again`() = runTest {
        val rig = rig()
        rig.postable()
        rig.poster.sendReply = LinkedInSendResult.Unknown
        rig.submit()
        rig.poster.sendReply = LinkedInSendResult.Posted

        val second = rig.submit()

        assertEquals(LinkedInPostState.Posted, second)
        assertEquals(listOf("page:games/fixture/", "send", "page:games/fixture/", "send"), rig.poster.calls)
        assertEquals(setOf("fixture/2026-11-05"), rig.stored().postedRefs)
        assertEquals(emptySet<String>(), rig.stored().sendingRefs)
    }

    @Test
    fun `with posting off, a paused link or a day that is over, LinkedIn is not asked at all`() = runTest {
        val off = rig().apply { postable(); link.setPosting(null) }
        val paused = rig().apply { postable(); link.pause(Kind.Challenge) }
        val over = rig().apply { postable(); calendar.setAll(today.plusDays(1)) }

        val results = listOf(off, paused, over).map { it.submit() }

        assertEquals(
            listOf(LinkedInPostState.Hidden, LinkedInPostState.Paused, LinkedInPostState.NotToday),
            results,
        )
        assertEquals(List(3) { emptyList<String>() }, listOf(off, paused, over).map { it.source.calls + it.poster.calls })
    }

    @Test
    fun `posting turned off during the check stops the post before the page is read`() = runTest {
        val rig = rig()
        rig.postable()
        val door = rig.door(
            source = object : LinkedInAccountSource by rig.source {
                override suspend fun ownResult(profileId: String, gameTypeId: Int, puzzleNumber: Int) =
                    rig.source.ownResult(profileId, gameTypeId, puzzleNumber).also { rig.link.setPosting(null) }
            },
        )

        val result = door.submit(rig.game, rig.today)

        assertEquals(LinkedInPostState.Hidden, result)
        assertEquals(emptyList<String>(), rig.poster.calls)
    }

    @Test
    fun `the game's day ending during the page read stops the post before the send`() = runTest {
        val rig = rig()
        rig.postable()
        val door = rig.door(
            poster = object : LinkedInResultPoster by rig.poster {
                override suspend fun gamePage(gamePath: String) =
                    rig.poster.gamePage(gamePath).also { rig.calendar.setAll(rig.today.plusDays(1)) }
            },
        )

        val result = door.submit(rig.game, rig.today)

        assertEquals(LinkedInPostState.NotToday, result)
        assertEquals(listOf("page:games/fixture/"), rig.poster.calls)
        assertEquals(emptySet<String>(), rig.stored().sendingRefs)
    }

    @Test
    fun `each of the three requests takes one call slot`() = runTest {
        val rig = rig(callLimit = 5)
        rig.postable()

        rig.submit()

        assertEquals(listOf(true, true, false), List(3) { rig.budget.tryAcquire() })
    }

    @Test
    fun `a check that could not reach LinkedIn keeps the day card's cached read`() = runTest {
        val rig = rig()
        rig.postable()
        rig.logOwnRead()
        rig.source.respondOwn("me", 6, 712, LinkedInRead.Offline)

        rig.submit()

        assertEquals(5_000L, rig.ownRead()?.fetchedAtMs)
    }
}
