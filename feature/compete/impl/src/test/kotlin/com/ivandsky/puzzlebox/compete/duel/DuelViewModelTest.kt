package com.ivandsky.puzzlebox.compete.duel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.compete.create.DuelSignIn
import com.ivandsky.puzzlebox.data.identity.AuthError
import com.ivandsky.puzzlebox.data.identity.AuthResult
import com.ivandsky.puzzlebox.data.match.WriteResult
import com.ivandsky.puzzlebox.domain.SeatAction
import kotlinx.coroutines.CompletableDeferred
import com.ivandsky.puzzlebox.auth.GoogleIdToken
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.match.ClaimResult
import com.ivandsky.puzzlebox.data.match.LegRef
import com.ivandsky.puzzlebox.data.match.MatchDraft
import com.ivandsky.puzzlebox.data.match.MatchFetch
import com.ivandsky.puzzlebox.data.match.MatchLeg
import com.ivandsky.puzzlebox.data.match.MatchRepository
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.domain.ObserveMatch
import com.ivandsky.puzzlebox.domain.UpdateMatchSeat
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeMatchLocalRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchResultOutbox
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The duel screen's state holder over the in-memory server: Ann challenges, Bob opens the code. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DuelViewModelTest {

    @get:Rule
    val mainDispatcher = MainDispatcherRule()

    private val code = ShareCode.parse("K7Q2MX")!!
    private val ann = Player("ann", "Ann", null)
    private val bob = Player("bob", "Bob", null)
    private val now = Instant.parse("2026-11-09T10:00:00Z")
    private val date = LocalDate.of(2026, 11, 9)

    private val server = FakeMatchRepository().apply { this.now = this@DuelViewModelTest.now }
    private var repo: MatchRepository = server
    private val local = FakeMatchLocalRepository()
    private val identity = FakeIdentityService()
    private val completions = FakeCompletionRepository()
    private val registry = TestGames.registry(listOf(TestGames.alpha, TestGames.beta))
    private var token: GoogleIdToken = GoogleIdToken.Available("token")
    private val idTokens = object : GoogleIdTokenProvider {
        override suspend fun idToken(activityContext: Context): GoogleIdToken = token
    }
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun viewAs(player: Player?) {
        identity.currentIdentity = player?.let { Identity(it.uid, it.name, null) }
        server.signedInUid = player?.uid
    }

    private fun viewModel(rawCode: String = code.value) = DuelViewModel(
        code = rawCode,
        observeMatch = ObserveMatch(repo, FakeMatchResultOutbox(), local, identity, registry) { now },
        updateSeat = UpdateMatchSeat(repo, local, identity),
        completions = completions,
        identity = identity,
        idTokens = idTokens,
        registry = registry,
    )

    private fun TestScope.started(vm: DuelViewModel): DuelViewModel {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
        return vm
    }

    /** Ann opens the duel; [invited] makes it a rematch invite. The viewer is then [viewer]. */
    private suspend fun annChallenges(viewer: Player?, invited: String? = null, claimedBy: Player? = null) {
        viewAs(ann)
        val legs = listOf(
            MatchLeg(LegRef.Daily(TestGames.alpha, date, 1), null),
            MatchLeg(LegRef.Daily(TestGames.beta, date, 2), null),
        )
        server.create(MatchDraft(code, 24, ann, invited, legs, includeFriends = false, rematchOf = null))
        if (claimedBy != null) {
            viewAs(claimedBy)
            server.claim(code, claimedBy)
        }
        viewAs(viewer)
    }

    private fun invite(vm: DuelViewModel) = vm.state.value as DuelUiState.Invite

    @Test
    fun signedOutShowsSignInCalloutWithAcceptDisabled() = runTest {
        annChallenges(viewer = null)
        val vm = started(viewModel())

        val state = invite(vm)

        assertEquals(InviteKind.SignedOut, state.kind)
        assertNull(state.details)
        assertEquals(code, state.code)
    }

    @Test
    fun signedInShowsAccept() = runTest {
        annChallenges(viewer = bob)
        val vm = started(viewModel())

        val state = invite(vm)

        assertEquals(InviteKind.Open, state.kind)
        val details = state.details!!
        assertEquals(ann, details.challenger)
        assertEquals(listOf("Alpha", "Beta"), details.games.map { it.name })
        assertEquals(2, details.rounds)
        assertEquals(now.plusSeconds(24 * 3600), details.deadline)
        assertFalse(details.solvedBefore)
        assertFalse(details.canDecline)
    }

    @Test
    fun aMatchWithAnotherOpponentIsTaken() = runTest {
        annChallenges(viewer = Player("cy", "Cy", null), claimedBy = bob)
        val vm = started(viewModel())

        assertEquals(InviteKind.Taken, invite(vm).kind)
    }

    @Test
    fun aCancelledMatchIsCancelled() = runTest {
        annChallenges(viewer = ann)
        server.cancel(code)
        viewAs(bob)
        val vm = started(viewModel())

        assertEquals(InviteKind.Cancelled, invite(vm).kind)
    }

    @Test
    fun challengerOnOwnUnclaimedCodeSeesWaitingAndCancel() = runTest {
        annChallenges(viewer = ann)
        val vm = started(viewModel())

        assertEquals(InviteKind.Waiting, invite(vm).kind)
        vm.cancel()

        assertTrue(server.matches.getValue(code).cancelled)
        val state = invite(vm)
        assertEquals(InviteKind.Cancelled, state.kind)
        assertTrue(state.details!!.viewerIsChallenger)
    }

    @Test
    fun notFoundOfflineAndUpdateAppMapToTheirStates() = runTest {
        viewAs(bob)
        val vm = started(viewModel())
        assertEquals(DuelUiState.NotFound, vm.state.value)

        server.online = false
        assertEquals(DuelUiState.Offline, vm.state.value)

        repo = object : MatchRepository by server {
            override fun observe(code: ShareCode): Flow<MatchFetch> = flowOf(MatchFetch.UnsupportedVersion(2))
        }
        val outdated = started(viewModel())
        assertEquals(DuelUiState.UpdateApp, outdated.state.value)
    }

    @Test
    fun aCodeThatIsNotACodeIsNotFound() = runTest {
        viewAs(bob)

        assertEquals(DuelUiState.NotFound, started(viewModel("not a code")).state.value)
    }

    @Test
    fun retryReadsTheDuelAgainAfterOffline() = runTest {
        annChallenges(viewer = bob)
        server.online = false
        val vm = started(viewModel())
        assertEquals(DuelUiState.Offline, vm.state.value)

        server.online = true
        vm.retry()

        assertEquals(InviteKind.Open, invite(vm).kind)
    }

    @Test
    fun acceptClaimsTheSeatAndTheScreenBecomesParticipant() = runTest {
        annChallenges(viewer = bob)
        val vm = started(viewModel())

        vm.accept()

        assertEquals(bob, server.matches.getValue(code).opponent)
        val state = vm.state.value as DuelUiState.Participant
        assertEquals("bob", state.view.me)
    }

    @Test
    fun acceptThatLosesTheRaceShowsTaken() = runTest {
        annChallenges(viewer = bob)
        repo = object : MatchRepository by server {
            override suspend fun claim(code: ShareCode, me: Player) = ClaimResult.Taken
        }
        val vm = started(viewModel())

        vm.accept()

        val state = invite(vm)
        assertEquals(InviteKind.Taken, state.kind)
        assertFalse(state.working)
    }

    @Test
    fun acceptWhenCancelledShowsCancelled() = runTest {
        annChallenges(viewer = bob)
        repo = object : MatchRepository by server {
            override suspend fun claim(code: ShareCode, me: Player) = ClaimResult.Cancelled
        }
        val vm = started(viewModel())

        vm.accept()

        assertEquals(InviteKind.Cancelled, invite(vm).kind)
    }

    @Test
    fun acceptAfterTheDeadlineKeepsTheInviteWithAClosedNotice() = runTest {
        annChallenges(viewer = bob)
        repo = object : MatchRepository by server {
            override suspend fun claim(code: ShareCode, me: Player) = ClaimResult.Closed
        }
        val vm = started(viewModel())

        vm.accept()

        val state = invite(vm)
        assertEquals(InviteKind.Open, state.kind)
        assertEquals(InviteNotice.Closed(SeatAction.Claim), state.notice)
    }

    @Test
    fun acceptOfflineKeepsTheInviteAndShowsRetry() = runTest {
        annChallenges(viewer = bob)
        repo = object : MatchRepository by server {
            override suspend fun claim(code: ShareCode, me: Player) = ClaimResult.Offline
        }
        val vm = started(viewModel())

        vm.accept()

        val state = invite(vm)
        assertEquals(InviteKind.Open, state.kind)
        assertEquals(InviteNotice.Offline(SeatAction.Claim), state.notice)
        assertFalse(state.working)
    }

    @Test
    fun anotherTryClearsTheNotice() = runTest {
        annChallenges(viewer = bob)
        var offline = true
        repo = object : MatchRepository by server {
            override suspend fun claim(code: ShareCode, me: Player) =
                if (offline) ClaimResult.Offline else server.claim(code, me)
        }
        val vm = started(viewModel())
        vm.accept()
        offline = false

        vm.accept()

        assertTrue(vm.state.value is DuelUiState.Participant)
    }

    @Test
    fun declineOfARematchInviteDeclinesAndCloses() = runTest {
        annChallenges(viewer = bob, invited = bob.uid)
        val vm = started(viewModel())
        assertTrue(invite(vm).details!!.canDecline)
        val closed = async { vm.closes.first() }

        vm.decline()

        assertTrue(server.matches.getValue(code).declined)
        assertEquals(Unit, withTimeoutOrNull(1_000) { closed.await() })
    }

    @Test
    fun declineOfALinkInviteClosesWithoutTouchingTheServer() = runTest {
        annChallenges(viewer = bob)
        val vm = started(viewModel())
        assertFalse(invite(vm).details!!.canDecline)
        val closed = async { vm.closes.first() }

        vm.decline()

        assertFalse(server.matches.getValue(code).declined)
        assertEquals(Unit, withTimeoutOrNull(1_000) { closed.await() })
    }

    @Test
    fun solvedBeforeNoteShowsWhenAGameInTheDuelWasAlreadySolved() = runTest {
        annChallenges(viewer = bob)
        val vm = started(viewModel())
        assertFalse(invite(vm).details!!.solvedBefore)

        completions.setRecords(
            listOf(
                CompletionRecord(
                    puzzleRef = TestGames.Ref(TestGames.alpha, PuzzleKey.Daily(date, 1)),
                    time = 1_000,
                    hints = 0,
                    stats = RecordStats(1, "{}"),
                    isRecord = true,
                ),
            ),
        )

        assertTrue(invite(vm).details!!.solvedBefore)
    }

    @Test
    fun signInSignsTheViewerInAndTheInviteOpens() = runTest {
        annChallenges(viewer = null)
        val vm = started(viewModel())
        assertEquals(InviteKind.SignedOut, invite(vm).kind)
        server.signedInUid = bob.uid
        identity.nextSignInResult = AuthResult.Ok(Identity(bob.uid, bob.name, null))

        vm.signIn(context)

        assertEquals(listOf("token"), identity.signInCalls)
        val state = invite(vm)
        assertEquals(InviteKind.Open, state.kind)
        assertEquals(DuelSignIn.Idle, state.signIn)
    }

    @Test
    fun aDismissedAccountPickerLeavesTheSignInCallout() = runTest {
        annChallenges(viewer = null)
        val vm = started(viewModel())
        token = GoogleIdToken.Unavailable(GoogleIdToken.Unavailable.Reason.CANCELLED)

        vm.signIn(context)

        val state = invite(vm)
        assertEquals(InviteKind.SignedOut, state.kind)
        assertEquals(DuelSignIn.Idle, state.signIn)
        assertTrue(identity.signInCalls.isEmpty())
    }

    @Test
    fun aFailedSignInIsFailedAndAnOfflineOneIsOffline() = runTest {
        annChallenges(viewer = null)
        val vm = started(viewModel())
        identity.nextSignInResult = AuthResult.Err(AuthError.Rejected("no"))

        vm.signIn(context)
        assertEquals(DuelSignIn.Failed, invite(vm).signIn)

        identity.nextSignInResult = AuthResult.Err(AuthError.Unavailable("down"))
        vm.signIn(context)
        assertEquals(DuelSignIn.Offline, invite(vm).signIn)
        assertEquals(InviteKind.SignedOut, invite(vm).kind)
    }

    @Test
    fun aDoubleTapOnAcceptSendsOneClaim() = runTest {
        annChallenges(viewer = bob)
        val gate = CompletableDeferred<Unit>()
        var claims = 0
        repo = object : MatchRepository by server {
            override suspend fun claim(code: ShareCode, me: Player): ClaimResult {
                claims++
                gate.await()
                return server.claim(code, me)
            }
        }
        val vm = started(viewModel())

        vm.accept()
        vm.accept()
        assertTrue(invite(vm).working)
        gate.complete(Unit)

        assertEquals(1, claims)
        assertTrue(vm.state.value is DuelUiState.Participant)
    }

    @Test
    fun aDoubleTapOnDeclineSendsOneDecline() = runTest {
        annChallenges(viewer = bob, invited = bob.uid)
        val gate = CompletableDeferred<Unit>()
        var declines = 0
        repo = object : MatchRepository by server {
            override suspend fun decline(code: ShareCode): WriteResult {
                declines++
                gate.await()
                return server.decline(code)
            }
        }
        val vm = started(viewModel())

        vm.decline()
        vm.decline()
        gate.complete(Unit)

        assertEquals(1, declines)
    }

    @Test
    fun anOfflineCancelKeepsTheWaitingCardWithItsOwnNotice() = runTest {
        annChallenges(viewer = ann)
        repo = object : MatchRepository by server {
            override suspend fun cancel(code: ShareCode) = WriteResult.Offline
        }
        val vm = started(viewModel())

        vm.cancel()

        val state = invite(vm)
        assertEquals(InviteKind.Waiting, state.kind)
        assertEquals(InviteNotice.Offline(SeatAction.Cancel), state.notice)
    }
}
