package com.ivandsky.puzzlebox.domain

import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.match.ClaimResult
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.LocalMatch
import com.ivandsky.puzzlebox.data.match.LocalRole
import com.ivandsky.puzzlebox.data.match.MatchDraft
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.MatchRepository
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.data.match.WriteResult
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeMatchLocalRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Taking, withdrawing and refusing a duel's seat, against the in-memory server: each action's result
 * and what it leaves in the local list.
 */
class UpdateMatchSeatTest {

    private val server = FakeMatchRepository().apply { now = Instant.parse("2026-11-09T10:00:00Z") }
    private val repo = RecordingMatchRepository(server)
    private val local = FakeMatchLocalRepository()
    private val identity = FakeIdentityService(BO_IDENTITY)
    private val seat = UpdateMatchSeat(repo, local, identity)

    private suspend fun rows(): List<LocalMatch> = local.observeAll().first()

    /** Ann opens [CODE] (optionally inviting [invited]); the signed-in user is then [asUid]. */
    private suspend fun annInvites(invited: String? = null, asUid: String? = BO.uid) {
        server.signedInUid = ANN.uid
        val draft = MatchDraft(CODE, 24, ANN, invited, listOf(MatchLeg(LegRef.Custom(GameId("alpha"), CODE), null)), false, null)
        server.create(draft)
        server.signedInUid = asUid
    }

    @Test
    fun claimRemembersTheMatchAsParticipantWithTheChallengersName() = runTest {
        annInvites()

        assertEquals(SeatResult.Done, seat(CODE, SeatAction.Claim))

        assertEquals(BO, server.matches.getValue(CODE).opponent)
        val row = rows().single()
        assertEquals(LocalRole.Participant, row.role)
        assertEquals(ANN.name, row.opponentName)
    }

    @Test
    fun claimedSeatDoesNotDowngradeAnExistingParticipantRow() = runTest {
        annInvites()
        local.remember(CODE, LocalRole.Participant, null)

        seat(CODE, SeatAction.Claim)

        assertEquals(LocalRole.Participant, rows().single().role)
    }

    @Test
    fun claimWhileSignedOutIsSignedOutAndTouchesNothing() = runTest {
        annInvites()
        identity.currentIdentity = null

        assertEquals(SeatResult.SignedOut, seat(CODE, SeatAction.Claim))

        assertTrue(repo.calls.isEmpty())
        assertTrue(rows().isEmpty())
    }

    @Test
    fun claimOfASeatSomeoneElseTookIsTaken() = runTest {
        annInvites()
        server.signedInUid = CAL.uid
        server.claim(CODE, CAL)
        server.signedInUid = BO.uid
        assertEquals(SeatResult.Taken, seat(CODE, SeatAction.Claim))
        assertTrue(rows().isEmpty())
    }

    @Test
    fun claimOfACancelledMatchIsCancelled() = runTest {
        annInvites()
        server.signedInUid = ANN.uid
        server.cancel(CODE)
        server.signedInUid = BO.uid

        assertEquals(SeatResult.Cancelled, seat(CODE, SeatAction.Claim))
        assertTrue(rows().isEmpty())
    }

    @Test
    fun claimAfterTheDeadlineIsClosed() = runTest {
        annInvites()
        server.now = Instant.parse("2026-11-11T10:00:00Z")

        assertEquals(SeatResult.Closed, seat(CODE, SeatAction.Claim))
        assertTrue(rows().isEmpty())
    }

    @Test
    fun claimOfflineIsOffline() = runTest {
        annInvites()
        server.online = false

        assertEquals(SeatResult.Offline, seat(CODE, SeatAction.Claim))
        assertTrue(rows().isEmpty())
    }

    @Test
    fun claimThatTheServerRejectsCarriesItsMessage() = runTest {
        annInvites(invited = CAL.uid)

        val result = seat(CODE, SeatAction.Claim)

        assertTrue(result is SeatResult.Rejected && result.message != null)
        assertTrue(rows().isEmpty())
    }

    @Test
    fun claimSignedOutOnTheServerIsSignedOut() = runTest {
        annInvites(asUid = null)

        assertEquals(SeatResult.SignedOut, seat(CODE, SeatAction.Claim))
        assertTrue(rows().isEmpty())
    }

    @Test
    fun claimWritesTheTrimmedNameCutToTheRulesBound() = runTest {
        annInvites()
        identity.currentIdentity = BO_IDENTITY.copy(displayName = "  ${"x".repeat(100)}  ")
        seat(CODE, SeatAction.Claim)
        assertEquals("x".repeat(64), server.matches.getValue(CODE).opponent?.name)
    }

    @Test
    fun claimWithABlankNameWritesTheAnonymousLabel() = runTest {
        annInvites()
        identity.currentIdentity = BO_IDENTITY.copy(displayName = " \t ")
        seat(CODE, SeatAction.Claim)
        assertEquals("Player", server.matches.getValue(CODE).opponent?.name)
    }

    @Test
    fun cancelMapsWriteResults() = runTest {
        annInvites(asUid = ANN.uid)
        server.online = false
        assertEquals(SeatResult.Offline, seat(CODE, SeatAction.Cancel))
        server.online = true
        assertEquals(SeatResult.Done, seat(CODE, SeatAction.Cancel))
        assertTrue(server.matches.getValue(CODE).cancelled)
        annInvites(asUid = BO.uid)
        assertTrue(seat(CODE, SeatAction.Cancel) is SeatResult.Rejected)
    }

    @Test
    fun declineOfARematchInviteAddressedToMeDeclinesRemotelyThenDismisses() = runTest {
        annInvites(invited = BO.uid)
        local.remember(CODE, LocalRole.Invite, ANN.name)

        assertEquals(SeatResult.Done, seat(CODE, SeatAction.Decline))

        assertTrue(server.matches.getValue(CODE).declined)
        assertTrue(rows().single().dismissed)
    }

    @Test
    fun declineOfALinkInviteOnlyDismissesLocally() = runTest {
        annInvites()
        local.remember(CODE, LocalRole.Invite, ANN.name)

        assertEquals(SeatResult.Done, seat(CODE, SeatAction.Decline))

        assertTrue("decline" !in repo.calls)
        assertTrue(rows().single().dismissed)
    }

    @Test
    fun declineThatTheServerRefusesKeepsTheLocalInvite() = runTest {
        annInvites(invited = BO.uid)
        local.remember(CODE, LocalRole.Invite, ANN.name)
        val scripted = UpdateMatchSeat(RefusingMatchRepository(server, WriteResult.Offline), local, identity)
        assertEquals(SeatResult.Offline, scripted(CODE, SeatAction.Decline))
        val rejected = UpdateMatchSeat(RefusingMatchRepository(server, WriteResult.Rejected("no")), local, identity)
        assertEquals(SeatResult.Rejected("no"), rejected(CODE, SeatAction.Decline))
        assertTrue(!rows().single().dismissed)
    }

    @Test
    fun declineWhileTheMatchCannotBeReadIsOfflineAndKeepsTheInvite() = runTest {
        annInvites(invited = BO.uid)
        local.remember(CODE, LocalRole.Invite, ANN.name)
        server.online = false

        assertEquals(SeatResult.Offline, seat(CODE, SeatAction.Decline))

        assertTrue(!rows().single().dismissed)
        assertTrue("decline" !in repo.calls)
    }

    /** Records which write calls reach the server. */
    private class RecordingMatchRepository(private val server: FakeMatchRepository) : MatchRepository by server {
        val calls = mutableListOf<String>()
        override suspend fun claim(code: ShareCode, me: Player): ClaimResult {
            calls += "claim"
            return server.claim(code, me)
        }
        override suspend fun decline(code: ShareCode): WriteResult {
            calls += "decline"
            return server.decline(code)
        }
    }

    /** Reads the match online but answers every decline with [result]. */
    private class RefusingMatchRepository(private val server: FakeMatchRepository, private val result: WriteResult) :
        MatchRepository by server {
        override suspend fun decline(code: ShareCode): WriteResult = result
        override fun observe(code: ShareCode) = server.apply { online = true }.observe(code)
    }

    private companion object {
        val CODE = ShareCode.parse("K7Q2MX")!!
        val BO_IDENTITY = Identity(uid = "bo", displayName = "Bo", photoUrl = null)
        val ANN = Player("ann", "Ann", null)
        val BO = Player(BO_IDENTITY.uid, BO_IDENTITY.displayName, null)
        val CAL = Player("cal", "Cal", null)
    }
}
