package com.ivandsky.puzzlebox.domain

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.LegResult
import com.ivandsky.puzzlebox.data.match.LocalRole
import com.ivandsky.puzzlebox.data.match.MatchDraft
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.MatchRepository
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.data.match.ResultPostState
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
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Duels list read model over the rules-faithful fakes; Ann challenges, Bob is the other seat. Every match is
 * created at time 0 on the server clock and the use case reads the virtual scheduler.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ObserveMyDuelsTest {

    private val ann = Player("ann", "Ann", null)
    private val bob = Player("bob", "Bob", null)

    private val server = FakeMatchRepository()
    private var sealedReads = 0
    private val listeners = mutableListOf<ShareCode>()
    private val repo = object : MatchRepository by server {
        override fun observe(code: ShareCode) = server.observe(code).onStart { listeners += code }

        override suspend fun readResult(code: ShareCode, legIndex: Int, uid: String): ResultRead {
            sealedReads++
            return server.readResult(code, legIndex, uid)
        }
    }
    private val outbox = FakeMatchResultOutbox()
    private val local = FakeMatchLocalRepository()
    private val identity = FakeIdentityService()

    private val closesAtMs = 30L * 3_600_000
    private val deadlineMs = 24L * 3_600_000

    private fun TestScope.observe(): Flow<MyDuels> =
        ObserveMyDuels(repo, outbox, local, identity) { Instant.ofEpochMilli(testScheduler.currentTime) }()

    private fun viewAs(player: Player) {
        identity.currentIdentity = Identity(player.uid, player.name, null)
        server.signedInUid = player.uid
    }

    private fun code(value: String) = ShareCode.parse(value)!!

    private fun legs(count: Int) = List(count) { index ->
        MatchLeg(LegRef.Daily(TestGames.alpha, LocalDate.of(2026, 11, 1 + index), index + 1), authorUid = null)
    }

    /** Ann creates [code]; when [claimedBy] is set that player takes the seat. The viewer is left as Ann. */
    private suspend fun duel(
        code: ShareCode,
        legCount: Int = 2,
        claimedBy: Player? = null,
        invited: Player? = null,
        hours: Int = 24,
    ) {
        viewAs(ann)
        server.create(MatchDraft(code, hours, ann, invited?.uid, legs(legCount), includeFriends = false, rematchOf = null))
        if (claimedBy != null) {
            viewAs(claimedBy)
            server.claim(code, claimedBy)
            viewAs(ann)
        }
    }

    private fun result(uid: String, leg: Int) = LegResult(uid, leg, 60_000, 0, 1_000, false)

    /** [player] takes [leg]: an outbox row in [state], and for a Posted row the server result too. */
    private suspend fun takes(player: Player, code: ShareCode, leg: Int, state: ResultPostState = Posted) {
        outbox.enqueue(code, result(player.uid, leg))
        outbox.update(code, leg, player.uid, state, attempts = 1)
        if (state == Posted) {
            val viewer = server.signedInUid
            server.signedInUid = player.uid
            server.post(code, result(player.uid, leg))
            server.signedInUid = viewer
        }
    }

    private suspend fun ReceiveTurbine<MyDuels>.latest(testScope: TestScope): MyDuels {
        testScope.testScheduler.runCurrent()
        return expectMostRecentItem()
    }

    private fun MyDuels.kinds() = rows.associate { it.code to it.kind }

    @Test
    fun eachStateMapsToItsKind() = runTest {
        val invited = code("K7Q2M1")
        val yourMove = code("K7Q2M2")
        val reveal = code("K7Q2M3")
        val waiting = code("K7Q2M4")
        val unclaimed = code("K7Q2M5")
        duel(invited, invited = bob)
        duel(yourMove, claimedBy = bob)
        duel(reveal, claimedBy = bob)
        duel(waiting, claimedBy = bob)
        duel(unclaimed)
        for (leg in 0..1) {
            takes(ann, reveal, leg); takes(bob, reveal, leg)
            takes(ann, waiting, leg)
            takes(ann, unclaimed, leg)
        }

        viewAs(ann)
        observe().test {
            val rows = latest(this@runTest).kinds()
            assertEquals(
                mapOf(
                    invited to DuelRowKind.Unclaimed, yourMove to DuelRowKind.YourMove,
                    reveal to DuelRowKind.RevealReady, waiting to DuelRowKind.Waiting,
                    unclaimed to DuelRowKind.Unclaimed,
                ),
                rows,
            )
            viewAs(bob)
            assertEquals(
                mapOf(
                    invited to DuelRowKind.Invited, yourMove to DuelRowKind.YourMove,
                    reveal to DuelRowKind.RevealReady, waiting to DuelRowKind.YourMove,
                ),
                latest(this@runTest).kinds(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun anUnclaimedDuelIAmTheChallengerOfIsWaitingNotYourMove() = runTest {
        val none = code("K7Q2M1")
        val some = code("K7Q2M2")
        val all = code("K7Q2M3")
        val claimed = code("K7Q2M4")
        duel(none)
        duel(some)
        duel(all)
        duel(claimed, claimedBy = bob)
        takes(ann, some, 0)
        for (leg in 0..1) takes(ann, all, leg)

        viewAs(ann)
        observe().test {
            val duels = latest(this@runTest)
            assertEquals(
                mapOf(
                    none to DuelRowKind.Unclaimed, some to DuelRowKind.Unclaimed, all to DuelRowKind.Unclaimed,
                    claimed to DuelRowKind.YourMove,
                ),
                duels.kinds(),
            )
            // Only the claimed duel is mine to move on; nobody has accepted the others.
            assertEquals(1, duels.needYou)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aRowCountsMyTakenPuzzlesAndShowsTheOpponent() = runTest {
        val c = code("K7Q2M1")
        duel(c, legCount = 3, claimedBy = bob)
        takes(ann, c, 0, Pending)
        takes(ann, c, 1)
        viewAs(ann)
        observe().test {
            val row = latest(this@runTest).rows.single()
            assertEquals(2, row.legsDone)
            assertEquals(3, row.legs)
            assertEquals(bob, row.opponent)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun needYouCountsYourMoveRevealReadyAndInvited() = runTest {
        val invited = code("K7Q2M1")
        val yourMove = code("K7Q2M2")
        val reveal = code("K7Q2M3")
        val waiting = code("K7Q2M4")
        val unclaimed = code("K7Q2M5")
        duel(invited, invited = bob)
        duel(yourMove, claimedBy = bob)
        duel(reveal, claimedBy = bob)
        duel(waiting, claimedBy = bob)
        duel(unclaimed)
        for (leg in 0..1) {
            takes(bob, reveal, leg); takes(ann, reveal, leg)
            takes(bob, waiting, leg)
            takes(bob, unclaimed, leg)
        }
        viewAs(bob)
        observe().test {
            val duels = latest(this@runTest)
            assertEquals(
                mapOf(
                    invited to DuelRowKind.Invited, yourMove to DuelRowKind.YourMove,
                    reveal to DuelRowKind.RevealReady, waiting to DuelRowKind.Waiting,
                ),
                duels.kinds(),
            )
            assertEquals(3, duels.needYou)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aCachedLinkInviteIsInvitedAndADismissedOneIsHidden() = runTest {
        val open = code("K7Q2M1")
        val dismissed = code("K7Q2M2")
        duel(open, legCount = 4)
        duel(dismissed)
        server.now = Instant.ofEpochMilli(-closesAtMs)
        duel(code("K7Q2M4"))
        server.now = Instant.EPOCH
        local.remember(open, LocalRole.Invite, "Ann")
        local.remember(dismissed, LocalRole.Invite, "Ann")
        local.dismiss(dismissed)
        local.remember(code("K7Q2M4"), LocalRole.Invite, "Ann")
        viewAs(bob)
        observe().test {
            val row = latest(this@runTest).rows.single()
            assertEquals(open, row.code)
            assertEquals(DuelRowKind.Invited, row.kind)
            assertEquals(4, row.legs)
            assertEquals(0, row.legsDone)
            assertEquals(ann, row.opponent)
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(dismissed !in listeners)
    }

    @Test
    fun aCachedInviteIsDroppedOnceItsMatchCloses() = runTest {
        val c = code("K7Q2M1")
        duel(c)
        local.remember(c, LocalRole.Invite, "Ann")
        viewAs(bob)
        observe().test {
            assertEquals(listOf(DuelRowKind.Invited), latest(this@runTest).rows.map { it.kind })
            advanceTimeBy(closesAtMs + 1)
            assertTrue(latest(this@runTest).rows.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aCachedInviteWhoseMatchIsCancelledOrTakenIsDropped() = runTest {
        val cancelled = code("K7Q2M1")
        val taken = code("K7Q2M2")
        duel(cancelled)
        server.cancel(cancelled)
        duel(taken, claimedBy = Player("cat", "Cat", null))
        local.remember(cancelled, LocalRole.Invite, "Ann")
        local.remember(taken, LocalRole.Invite, "Ann")
        viewAs(bob)
        observe().test {
            assertTrue(latest(this@runTest).rows.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aServerRowWinsOverACachedInviteForTheSameCode() = runTest {
        val c = code("K7Q2M1")
        duel(c, claimedBy = bob)
        local.remember(c, LocalRole.Invite, "Ann")
        viewAs(bob)
        observe().test {
            val row = latest(this@runTest).rows.single()
            assertEquals(DuelRowKind.YourMove, row.kind)
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(c !in listeners)
    }

    @Test
    fun aClosedMatchStaysOnlyUntilItsRevealIsSeen() = runTest {
        val c = code("K7Q2M1")
        val cancelled = code("K7Q2M2")
        val declined = code("K7Q2M3")
        duel(c, claimedBy = bob)
        duel(cancelled)
        server.cancel(cancelled)
        duel(declined, invited = bob)
        viewAs(bob)
        server.decline(declined)
        viewAs(ann)
        observe().test {
            assertEquals(mapOf(c to DuelRowKind.YourMove), latest(this@runTest).kinds())
            advanceTimeBy(closesAtMs)
            assertEquals(mapOf(c to DuelRowKind.RevealReady), latest(this@runTest).kinds())
            local.remember(c, LocalRole.Participant, "Bob")
            local.markRevealSeen(c, Instant.ofEpochMilli(closesAtMs))
            assertTrue(latest(this@runTest).rows.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun rowsAreOrderedByNeedThenDeadline() = runTest {
        val waiting = code("K7Q2M1")
        val moveLate = code("K7Q2M2")
        val moveSoon = code("K7Q2M3")
        val reveal = code("K7Q2M4")
        val invited = code("K7Q2M5")
        duel(waiting, claimedBy = bob)
        duel(moveLate, claimedBy = bob, hours = 48)
        duel(moveSoon, claimedBy = bob)
        duel(reveal, claimedBy = bob, hours = 48)
        duel(invited, invited = bob, hours = 48)
        for (leg in 0..1) {
            takes(bob, waiting, leg)
            takes(ann, reveal, leg); takes(bob, reveal, leg)
        }
        viewAs(bob)
        observe().test {
            assertEquals(
                listOf(invited, moveSoon, moveLate, reveal, waiting),
                latest(this@runTest).rows.map { it.code },
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun waitingAndUnclaimedShareAGroupOrderedByDeadline() = runTest {
        val waiting = code("K7Q2M1")
        val unclaimed = code("K7Q2M2")
        duel(waiting, claimedBy = bob, hours = 48)
        duel(unclaimed)
        for (leg in 0..1) { takes(ann, waiting, leg); takes(ann, unclaimed, leg) }
        viewAs(ann)
        observe().test {
            assertEquals(listOf(unclaimed, waiting), latest(this@runTest).rows.map { it.code })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aListRefreshNeverReadsASealedResult() = runTest {
        val c = code("K7Q2M1")
        duel(c, claimedBy = bob)
        for (leg in 0..1) { takes(ann, c, leg); takes(bob, c, leg) }
        viewAs(ann)
        observe().test {
            assertEquals(DuelRowKind.RevealReady, latest(this@runTest).rows.single().kind)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(0, sealedReads)
    }

    @Test
    fun aRowMovesFromYourMoveToRevealReadyAtClosesAtWithNoUpstreamChange() = runTest {
        val c = code("K7Q2M1")
        duel(c, claimedBy = bob)
        takes(ann, c, 0)
        viewAs(ann)
        observe().test {
            val before = latest(this@runTest)
            assertEquals(DuelRowKind.YourMove, before.rows.single().kind)
            assertEquals(1, before.needYou)
            advanceTimeBy(deadlineMs - 1)
            expectNoEvents()
            advanceTimeBy(1)
            runCurrent()
            assertEquals(DuelRowKind.Waiting, awaitItem().rows.single().kind)
            advanceTimeBy(closesAtMs - deadlineMs - 1)
            expectNoEvents()
            advanceTimeBy(1)
            runCurrent()
            val after = awaitItem()
            assertEquals(DuelRowKind.RevealReady, after.rows.single().kind)
            assertEquals(1, after.needYou)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aClaimedDuelWithAnUnstartedPuzzleIsWaitingFromTheDeadline() = runTest {
        val c = code("K7Q2M1")
        duel(c, claimedBy = bob)
        takes(ann, c, 0)
        viewAs(ann)
        observe().test {
            assertEquals(DuelRowKind.YourMove, latest(this@runTest).rows.single().kind)
            advanceTimeBy(deadlineMs)
            val row = latest(this@runTest).rows.single()
            assertEquals(DuelRowKind.Waiting, row.kind)
            assertEquals(bob, row.opponent)
            assertEquals(1, row.legsDone)
            assertEquals(2, row.legs)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aRowMovesFromYourMoveToWaitingAtTheDeadlineWithNoUpstreamChange() = runTest {
        val c = code("K7Q2M1")
        duel(c, claimedBy = bob)
        viewAs(ann)
        observe().test {
            assertEquals(DuelRowKind.YourMove, latest(this@runTest).rows.single().kind)
            advanceTimeBy(deadlineMs - 1)
            expectNoEvents()
            advanceTimeBy(1)
            runCurrent()
            assertEquals(DuelRowKind.Waiting, awaitItem().rows.single().kind)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun needYouDropsAMatchPastItsDeadline() = runTest {
        val a = code("K7Q2M1")
        val b = code("K7Q2M2")
        duel(a, claimedBy = bob)
        duel(b, claimedBy = bob, hours = 48)
        viewAs(ann)
        observe().test {
            assertEquals(2, latest(this@runTest).needYou)
            advanceTimeBy(deadlineMs)
            val after = latest(this@runTest)
            assertEquals(1, after.needYou)
            assertEquals(mapOf(a to DuelRowKind.Waiting, b to DuelRowKind.YourMove), after.kinds())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aMatchPastItsDeadlineStillReachesRevealReadyAtClosesAt() = runTest {
        val c = code("K7Q2M1")
        duel(c, claimedBy = bob)
        viewAs(ann)
        observe().test {
            latest(this@runTest)
            advanceTimeBy(deadlineMs)
            assertEquals(DuelRowKind.Waiting, latest(this@runTest).rows.single().kind)
            advanceTimeBy(closesAtMs - deadlineMs)
            val after = latest(this@runTest)
            assertEquals(DuelRowKind.RevealReady, after.rows.single().kind)
            assertEquals(1, after.needYou)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aServedInviteIsDroppedFromTheDeadline() = runTest {
        val c = code("K7Q2M1")
        duel(c, invited = bob)
        viewAs(bob)
        observe().test {
            val before = latest(this@runTest)
            assertEquals(listOf(DuelRowKind.Invited), before.rows.map { it.kind })
            assertEquals(1, before.needYou)
            advanceTimeBy(deadlineMs - 1)
            expectNoEvents()
            advanceTimeBy(1)
            val after = latest(this@runTest)
            assertTrue(after.rows.isEmpty())
            assertEquals(0, after.needYou)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun aCachedLinkInviteIsDroppedFromTheDeadline() = runTest {
        val c = code("K7Q2M1")
        duel(c)
        local.remember(c, LocalRole.Invite, "Ann")
        viewAs(bob)
        observe().test {
            val before = latest(this@runTest)
            assertEquals(listOf(DuelRowKind.Invited), before.rows.map { it.kind })
            assertEquals(1, before.needYou)
            advanceTimeBy(deadlineMs - 1)
            expectNoEvents()
            advanceTimeBy(1)
            val after = latest(this@runTest)
            assertTrue(after.rows.isEmpty())
            assertEquals(0, after.needYou)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun signedOutIsEmptyAndZero() = runTest {
        val c = code("K7Q2M1")
        duel(c, claimedBy = bob)
        identity.currentIdentity = null
        server.signedInUid = null
        observe().test {
            assertEquals(MyDuels(emptyList(), 0), awaitItem())
            viewAs(ann)
            assertEquals(listOf(c), latest(this@runTest).rows.map { it.code })
            identity.currentIdentity = Identity(ann.uid, ann.name, null)
            server.signedInUid = null
            assertEquals(MyDuels(emptyList(), 0), latest(this@runTest))
            cancelAndIgnoreRemainingEvents()
        }
    }
}
