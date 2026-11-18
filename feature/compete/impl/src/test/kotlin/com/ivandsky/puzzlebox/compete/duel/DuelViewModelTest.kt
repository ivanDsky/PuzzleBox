package com.ivandsky.puzzlebox.compete.duel

import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakePublishedPuzzleRepository
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.common.flags.ReleaseFlags
import com.ivandsky.puzzlebox.domain.lineup.ObserveLegTitles
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.domain.LoadSharedPuzzle
import com.ivandsky.puzzlebox.domain.LoadLegPuzzle
import com.ivandsky.puzzlebox.common.dispatcher.DefaultDispatcherProvider
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
import com.ivandsky.puzzlebox.data.match.MatchResultSync
import com.ivandsky.puzzlebox.data.match.Player
import com.ivandsky.puzzlebox.domain.ObserveDuel
import com.ivandsky.puzzlebox.domain.ObserveMatch
import com.ivandsky.puzzlebox.domain.UpdateMatchSeat
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.domain.DeleteSelfDuel
import com.ivandsky.puzzlebox.domain.buildSelfMatch
import com.ivandsky.puzzlebox.testing.repository.FakeSelfMatchRepository
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeMatchLocalRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchRepository
import com.ivandsky.puzzlebox.testing.repository.FakeMatchResultOutbox
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
    private val self = FakeSelfMatchRepository()
    private val timeLeft = MutableStateFlow(true)
    private val identity = FakeIdentityService()
    private val completions = FakeCompletionRepository()
    private val received = FakeReceivedPuzzleRepository()
    private val sharedPuzzles = FakeSharedPuzzleRepository()
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

    private fun viewModel(
        rawCode: String = code.value,
        plays: SavedPlayRepository = FakeSavedPlayRepository(),
        flags: ReleaseFlags? = null,
    ) = DuelViewModel(
        code = rawCode,
        observeDuel = ObserveDuel(
            observeMatch = ObserveMatch(repo, FakeMatchResultOutbox(), local, identity, registry) { now },
            completions = completions,
            savedPlays = FakeSavedPlayRepository(),
        ),
        updateSeat = UpdateMatchSeat(repo, local, identity),
        resultSync = MatchResultSync { },
        identity = identity,
        idTokens = idTokens,
        registry = registry,
        deleteSelfDuel = DeleteSelfDuel(self, FakeMatchResultOutbox(), plays, local),
        showTimeLeft = timeLeft,
        loadLegPuzzle = LoadLegPuzzle(
            daily = FakeDailyRepository(),
            received = received,
            loadShared = LoadSharedPuzzle(
                registry = registry,
                shared = sharedPuzzles,
                received = received,
                budget = { Budget.within(1_000, System::nanoTime) },
                clock = { now },
            ),
        ),
        plays = GamePlayRegistry(emptyList()),
        dispatchers = DefaultDispatcherProvider(),
        legTitles = ObserveLegTitles(received, FakePublishedPuzzleRepository(), sharedPuzzles),
        releaseFlags = flags,
    )

    private fun TestScope.started(vm: DuelViewModel): DuelViewModel {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { } }
        return vm
    }

    /** Ann opens the duel; [invited] makes it a rematch invite. The viewer is then [viewer]. */
    private suspend fun annChallenges(viewer: Player?, invited: String? = null, claimedBy: Player? = null, lineup: List<LegRef>? = null) {
        viewAs(ann)
        val legs = (lineup ?: listOf(LegRef.Daily(TestGames.alpha, date, 1), LegRef.Daily(TestGames.beta, date, 2))).map { MatchLeg(it, null) }
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
        assertEquals(code, state.code)
    }

    @Test
    fun aSignedOutViewerSeesTheChallengerGamesAndDeadline() = runTest {
        annChallenges(viewer = null)
        val vm = started(viewModel())

        val details = invite(vm).details!!

        assertEquals(ann, details.challenger)
        assertEquals(listOf("Alpha", "Beta"), details.games.map { it.name })
        assertEquals(2, details.rounds)
        assertEquals(now.plusSeconds(24 * 3600), details.deadline)
        assertFalse(details.viewerIsChallenger)
        assertFalse(details.canDecline)
    }

    private val crown = ShareCode.parse("B8C3DF")!!
    private val unreadable = ShareCode.parse("D5F7GH")!!

    /** Daily alpha, a custom puzzle titled "Crown jewels", then a custom one no one can read. */
    private val mixedLineup = listOf(
        LegRef.Daily(TestGames.alpha, date, 1),
        LegRef.Custom(TestGames.beta, crown),
        LegRef.Custom(TestGames.alpha, unreadable),
    )

    private suspend fun crownIsSavedByTheViewer() = received.upsert(
        ReceivedPuzzle(
            code = crown, gameId = TestGames.beta, variant = TestGames.variant(TestGames.beta), sharedFormatVersion = 1,
            puzzleBytes = byteArrayOf(TestGames.MAGIC), title = "Crown jewels", authorName = "Cleo", note = "", authorTimeMs = 1_000L,
            authorHints = 0, authorUid = "cleo", openedAt = now, unpublished = false, postState = PostState.Pending,
            postAttempts = 0, difficulty = null,
        ),
    )

    private suspend fun crownIsPublic() {
        sharedPuzzles.signedInUid = "cleo"
        sharedPuzzles.publish(
            SharedPuzzleDoc(
                code = crown, formatVersion = SharedPuzzleDoc.FORMAT_VERSION, gameId = TestGames.beta.value,
                variant = TestGames.variant(TestGames.beta).value, shareCodecVersion = 1, payload = byteArrayOf(1),
                title = "Crown jewels", authorName = "Cleo", note = "", authorUid = "cleo", authorTimeMs = 1_000L,
                authorHints = 0, createdAtMs = 0L, unpublished = false, visibility = SharedPuzzleDoc.Visibility.LinkOnly,
            ),
        )
    }

    private val lineupOn = FakeReleaseFlags(ReleaseFlag.DuelLineup)
    private val expectedMixed = listOf("Alpha", "\"Crown jewels\"", "Alpha · ${unreadable.display}")

    @Test
    fun withTheLineupOnASignedInInviteeSeesEveryPuzzleInLegOrder() = runTest {
        crownIsSavedByTheViewer()
        annChallenges(viewer = bob, lineup = mixedLineup)
        val vm = started(viewModel(flags = lineupOn))

        assertEquals(expectedMixed, invite(vm).details!!.lineup!!.map { it.label })
    }

    @Test
    fun withTheLineupOnASignedOutViewerSeesTheSameLineupWithPublicTitles() = runTest {
        crownIsPublic()
        annChallenges(viewer = null, lineup = mixedLineup)
        val vm = started(viewModel(flags = lineupOn))

        assertEquals(InviteKind.SignedOut, invite(vm).kind)
        assertEquals(expectedMixed, invite(vm).details!!.lineup!!.map { it.label })
    }

    @Test
    fun anOfflineViewerWithNoSavedTitleSeesTheGameAndCodeNotAnError() = runTest {
        crownIsPublic()
        annChallenges(viewer = null, lineup = mixedLineup)
        sharedPuzzles.online = false
        val vm = started(viewModel(flags = lineupOn))

        assertEquals(listOf("Alpha", "Beta · ${crown.display}", "Alpha · ${unreadable.display}"), invite(vm).details!!.lineup!!.map { it.label })
    }

    @Test
    fun withTheLineupOffTheInviteKeepsTodaysGameChips() = runTest {
        crownIsPublic()
        annChallenges(viewer = bob, lineup = mixedLineup)
        val vm = started(viewModel())

        val details = invite(vm).details!!
        assertNull(details.lineup)
        assertEquals(listOf("Alpha", "Beta"), details.games.map { it.name })
    }

    @Test
    fun aSignedOutViewerOfAMissingMatchSeesNotFound() = runTest {
        viewAs(null)
        val vm = started(viewModel())

        assertEquals(DuelUiState.NotFound, vm.state.value)
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

    private val selfCode = ShareCode.parse("ABC234")!!
    private val firstSolve = Instant.parse("2026-09-14T08:00:00Z")

    /** A self duel saved on this device with a solve of its first puzzle: the viewer is [viewer], null when signed out. */
    private suspend fun selfDuel(viewer: Player?) {
        viewAs(viewer)
        val legs = listOf(
            MatchLeg(LegRef.Daily(TestGames.alpha, date, 1), null),
            MatchLeg(LegRef.Daily(TestGames.beta, date, 2), null),
        )
        val record = CompletionRecord(
            puzzleRef = TestGames.Ref(TestGames.alpha, PuzzleKey.Daily(date, puzzleNumber = 1)),
            time = 38_000L,
            hints = 0,
            stats = RecordStats(schemaVersion = 1, blob = "{}"),
            firstCompletedAt = firstSolve,
            isRecord = true,
        )
        val draft = buildSelfMatch(selfCode, now, false, legs, legs.map { record })
        self.save(draft.match, draft.past)
        completions.setRecords(listOf(record))
        repo = self
    }

    @Test
    fun aSelfDuelOpensAsAParticipantNamedByTheSignedInPlayerWithTheDayOfPastYoursFirstSolve() = runTest {
        selfDuel(ann)
        val vm = started(viewModel(selfCode.value))

        val state = vm.state.value as DuelUiState.Participant

        assertEquals("Ann", state.myName)
        assertEquals(mapOf(state.view.match.legs[0].ref.wire to firstSolve), state.firstSolvedAt)
        assertFalse(state.removeAsked)
    }

    @Test
    fun aSignedOutSelfDuelHasNoName() = runTest {
        selfDuel(null)
        val vm = started(viewModel(selfCode.value))

        val state = vm.state.value as DuelUiState.Participant

        assertNull(state.myName)
    }

    @Test
    fun removeAsksFirstAndKeepClosesTheAskWithoutDeleting() = runTest {
        selfDuel(ann)
        val vm = started(viewModel(selfCode.value))

        vm.askRemove()
        assertTrue((vm.state.value as DuelUiState.Participant).removeAsked)
        vm.keep()

        assertFalse((vm.state.value as DuelUiState.Participant).removeAsked)
        assertTrue(self.holds(selfCode))
    }

    @Test
    fun removeWithoutAskingDeletesNothing() = runTest {
        selfDuel(ann)
        val vm = started(viewModel(selfCode.value))

        vm.remove()

        assertTrue(self.holds(selfCode))
    }

    @Test
    fun confirmedRemoveDeletesTheDuelClosesTheScreenAndNeverShowsNotFound() = runTest {
        selfDuel(ann)
        val vm = started(viewModel(selfCode.value))
        val seen = mutableListOf<DuelUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect { seen += it } }
        val closed = async { vm.closes.first() }

        vm.askRemove()
        vm.remove()
        closed.await()

        assertFalse(self.holds(selfCode))
        assertTrue(seen.none { it === DuelUiState.NotFound })
        assertEquals(DuelUiState.Loading, vm.state.value)
    }

    @Test
    fun aFailingDeleteDoesNotCrashPutsTheConfirmBackAndKeepsTheScreenOpen() = runTest {
        selfDuel(ann)
        val failing = object : SavedPlayRepository by FakeSavedPlayRepository() {
            override suspend fun delete(slot: PlaySlot) = throw IllegalStateException("disk full")
        }
        val vm = started(viewModel(selfCode.value, plays = failing))
        val closed = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) { vm.closes.first() }

        vm.askRemove()
        vm.remove()

        assertFalse((vm.state.value as DuelUiState.Participant).removeAsked)
        assertFalse(closed.isCompleted)
        assertTrue(self.holds(selfCode))
    }

    @Test
    fun theParticipantCarriesTheTimeLeftSettingLive() = runTest {
        selfDuel(ann)
        val vm = started(viewModel(selfCode.value))
        assertTrue((vm.state.value as DuelUiState.Participant).timeLeftShown)

        timeLeft.value = false

        assertFalse((vm.state.value as DuelUiState.Participant).timeLeftShown)
    }
}
