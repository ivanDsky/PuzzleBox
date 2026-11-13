package com.ivandsky.puzzlebox.app.screens

import android.content.Context
import com.ivandsky.puzzlebox.auth.GoogleIdToken
import com.ivandsky.puzzlebox.auth.GoogleIdToken.Unavailable.Reason.CANCELLED
import com.ivandsky.puzzlebox.auth.GoogleIdToken.Unavailable.Reason.MALFORMED
import com.ivandsky.puzzlebox.auth.GoogleIdToken.Unavailable.Reason.NETWORK
import com.ivandsky.puzzlebox.auth.GoogleIdToken.Unavailable.Reason.NO_CREDENTIAL
import com.ivandsky.puzzlebox.auth.GoogleIdToken.Unavailable.Reason.OTHER
import com.ivandsky.puzzlebox.auth.GoogleIdToken.Unavailable.Reason.PROVIDER_UNAVAILABLE
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.data.identity.AuthError
import com.ivandsky.puzzlebox.data.identity.AuthResult
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.linkedin.LinkState
import com.ivandsky.puzzlebox.data.linkedin.LinkedInAccountData
import com.ivandsky.puzzlebox.data.linkedin.LinkedInLink
import com.ivandsky.puzzlebox.data.linkedin.LinkedInOwner
import com.ivandsky.puzzlebox.domain.ComputeStreak
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.domain.linkedin.LinkedInAccountView
import com.ivandsky.puzzlebox.domain.linkedin.ObserveLinkedInAccount
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.game.linkedin.LinkedInRead.Rejected.Kind
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.profile.accounts.LinkedInRowState
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInAccount
import com.ivandsky.puzzlebox.testing.linkedin.FakeLinkedInLink
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Profile ViewModel states (brief §4, A20): signed-out/in, sign-in results, "Playing since". */
class ProfileViewModelTest {

    private val day = LocalDate.of(2026, 10, 31)
    private val dispatcher = StandardTestDispatcher()

    private val zip = GameId("zip")
    private val tango = GameId("tango")

    private val registry = GameRegistry(listOf(TestGames.plugin(zip), TestGames.plugin(tango)))
    private val clock = Clock.fixed(Instant.parse("2026-10-31T12:00:00Z"), ZoneOffset.UTC)

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    private class FakeTokenProvider(var next: GoogleIdToken = GoogleIdToken.Available("tok")) :
        GoogleIdTokenProvider {
        val calls = mutableListOf<Context>()
        override suspend fun idToken(activityContext: Context): GoogleIdToken {
            calls += activityContext
            return next
        }
    }

    private data class Deps(
        val vm: ProfileViewModel,
        val identity: FakeIdentityService,
        val tokens: FakeTokenProvider,
    )

    private fun TestScope.viewModel(
        identity: FakeIdentityService = FakeIdentityService(initial = null),
        tokens: FakeTokenProvider = FakeTokenProvider(),
        completions: FakeCompletionRepository = FakeCompletionRepository(),
        link: LinkedInLink? = null,
        stored: FakeLinkedInAccount = FakeLinkedInAccount(),
    ): Deps {
        val history = ObserveGameHistory(registry, FakeDailyRepository(), completions)
        val vm = ProfileViewModel(
            identityService = identity,
            idTokenProvider = tokens,
            observeGameStats = ObserveGameStats(registry, history, ComputeStreak(GetPuzzleDay(registry, clock))),
            link = link,
            observeAccount = link?.let { ObserveLinkedInAccount(stored, completions, GetPuzzleDay(registry, clock), registry) },
        )
        // The state is observed only while subscribed (the route's lifecycle collector).
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        return Deps(vm, identity, tokens)
    }

    private val Deps.signedOut: ProfileUiState.Account.SignedOut
        get() = vm.uiState.value.account as ProfileUiState.Account.SignedOut

    private val Deps.signedIn: ProfileUiState.Account.SignedIn
        get() = vm.uiState.value.account as ProfileUiState.Account.SignedIn

    // Plain JVM test: the fake provider ignores the context, so a mock satisfies the seam.
    private val appContext: Context = org.mockito.Mockito.mock(Context::class.java)

    @Test
    fun `signed out is the initial state (A7-A15)`() = runTest(dispatcher) {
        val deps = viewModel()

        // Signed out carries no sign-in progress.
        assertEquals(ProfileUiState.Account.SignedOut(), deps.vm.uiState.value.account)
    }

    @Test
    fun `signing in through the credential flow signs in and signing out returns to signed out`() = runTest(dispatcher) {
        val deps = viewModel()

        deps.vm.onSignIn(appContext)
        advanceUntilIdle()

        // Signed in: the identity shows and no sign-in message is carried.
        assertEquals(
            Identity(uid = "fake-uid", displayName = "Fake Player", photoUrl = null),
            deps.signedIn.identity,
        )
        assertEquals(deps.identity.currentIdentity, deps.signedIn.identity)

        deps.vm.onSignOut()
        advanceUntilIdle()

        assertEquals(ProfileUiState.Account.SignedOut(), deps.vm.uiState.value.account)
    }

    @Test
    fun `playing since is the first solve's day, practice never counts - A20`() = runTest(dispatcher) {
        val completions = FakeCompletionRepository(
            listOf(
                record(zip, day, timeMs = 95_000, hints = 2),
                record(zip, day.minusDays(1), timeMs = 80_000, hints = 0),
                // Practice (no edition number) never counts.
                record(tango, day.minusDays(5), timeMs = 10_000, hints = 0, puzzleNumber = null),
            ),
        )

        val state = viewModel(completions = completions).vm.uiState.value

        assertEquals(day.minusDays(1), state.playingSince)
    }

    private fun record(game: GameId, on: LocalDate, timeMs: Long, hints: Int, puzzleNumber: Int? = 592) =
        CompletionRecord(
            puzzleRef = TestGames.Ref(game, PuzzleKey.Daily(on, puzzleNumber)),
            time = timeMs,
            hints = hints,
            stats = RecordStats(schemaVersion = 1, blob = "{}"),
        )

    // ---- Ticket 24: sign-in copy per failure class -------------------------------
    // Exact strings lock the copy; "Check your connection" may appear on the NETWORK
    // branches only (provider or backend).

    /** Signs in with the scripted inputs and returns the surfaced message. */
    private fun TestScope.signInMessage(
        tokens: FakeTokenProvider = FakeTokenProvider(),
        identity: FakeIdentityService = FakeIdentityService(initial = null),
    ): String {
        val deps = viewModel(tokens = tokens, identity = identity)
        deps.vm.onSignIn(appContext)
        advanceUntilIdle()
        return requireNotNull(deps.signedOut.signInMessage)
    }

    @Test
    fun `provider network failure keeps the connection wording`() = runTest(dispatcher) {
        assertEquals(
            "Sign-in is unavailable right now. Check your connection.",
            signInMessage(tokens = FakeTokenProvider(GoogleIdToken.Unavailable(NETWORK))),
        )
    }

    @Test
    fun `no credential gets the google-unavailable wording, identity stays null (A7)`() =
        runTest(dispatcher) {
            val deps = viewModel(tokens = FakeTokenProvider(GoogleIdToken.Unavailable(NO_CREDENTIAL)))

            deps.vm.onSignIn(appContext)
            advanceUntilIdle()

            assertEquals(
                "Google sign-in isn't available right now. Try again, or try another account.",
                deps.signedOut.signInMessage,
            )
            // Still signed out: no identity (A7).
            assertTrue(deps.vm.uiState.value.account is ProfileUiState.Account.SignedOut)
        }

    @Test
    fun `api availability failure gets the google-unavailable wording`() = runTest(dispatcher) {
        assertEquals(
            "Google sign-in isn't available right now. Try again, or try another account.",
            signInMessage(tokens = FakeTokenProvider(GoogleIdToken.Unavailable(PROVIDER_UNAVAILABLE))),
        )
    }

    @Test
    fun `user cancel gets the generic retry wording`() = runTest(dispatcher) {
        assertEquals(
            "Sign-in failed. Please try again.",
            signInMessage(tokens = FakeTokenProvider(GoogleIdToken.Unavailable(CANCELLED))),
        )
    }

    @Test
    fun `malformed credential gets the generic retry wording`() = runTest(dispatcher) {
        assertEquals(
            "Sign-in failed. Please try again.",
            signInMessage(tokens = FakeTokenProvider(GoogleIdToken.Unavailable(MALFORMED))),
        )
    }

    @Test
    fun `other provider failure gets the generic retry wording`() = runTest(dispatcher) {
        assertEquals(
            "Sign-in failed. Please try again.",
            signInMessage(tokens = FakeTokenProvider(GoogleIdToken.Unavailable(OTHER))),
        )
    }

    @Test
    fun `backend network failure keeps the connection wording`() = runTest(dispatcher) {
        assertEquals(
            "Sign-in is unavailable right now. Check your connection.",
            signInMessage(
                identity = FakeIdentityService(
                    initial = null,
                    signInResult = AuthResult.Err(AuthError.Unavailable("offline")),
                ),
            ),
        )
    }

    @Test
    fun `backend token-exchange rejection gets the google-unavailable wording`() =
        runTest(dispatcher) {
            assertEquals(
                "Google sign-in isn't available right now. Try again, or try another account.",
                signInMessage(
                    identity = FakeIdentityService(
                        initial = null,
                        signInResult = AuthResult.Err(AuthError.Rejected("bad token")),
                    ),
                ),
            )
        }

    @Test
    fun `backend malformed answer keeps its wording`() = runTest(dispatcher) {
        assertEquals(
            "The account response was unusable. Please try again.",
            signInMessage(
                identity = FakeIdentityService(
                    initial = null,
                    signInResult = AuthResult.Err(AuthError.Malformed("no uid")),
                ),
            ),
        )
    }

    @Test
    fun `backend unknown failure keeps its wording`() = runTest(dispatcher) {
        assertEquals(
            "Sign-in failed unexpectedly. Please try again.",
            signInMessage(
                identity = FakeIdentityService(
                    initial = null,
                    signInResult = AuthResult.Err(AuthError.Unknown(IllegalStateException("boom"))),
                ),
            ),
        )
    }

    @Test
    fun `sign-out clears identity`() = runTest(dispatcher) {
        val deps = viewModel(identity = FakeIdentityService(initial = Identity("u1", "Ann", null)))
        advanceUntilIdle()
        assertTrue(deps.vm.uiState.value.account is ProfileUiState.Account.SignedIn)

        deps.vm.onSignOut()
        advanceUntilIdle()

        // Signed out: no identity.
        assertTrue(deps.vm.uiState.value.account is ProfileUiState.Account.SignedOut)
        assertEquals(1, deps.identity.signOutCalls.size)
    }

    // ---- LinkedIn row: follows the link and the phone's stored account ------------

    private val emptySummary = LinkedInAccountView(null, null, 0, 0, 0, emptyList(), null)

    @Test
    fun `with the flag off nothing LinkedIn is in the state`() = runTest(dispatcher) {
        assertEquals(null, viewModel(link = null).vm.uiState.value.linkedIn)
    }

    @Test
    fun `the row follows the link from not connected to connected and paused`() = runTest(dispatcher) {
        val link = FakeLinkedInLink()
        val deps = viewModel(link = link)
        assertEquals(LinkedInRowState.NotConnected, deps.vm.uiState.value.linkedIn)

        link.connect("me", consentAtMs = 1L)
        advanceUntilIdle()
        assertEquals(LinkedInRowState.Connected(emptySummary), deps.vm.uiState.value.linkedIn)

        link.pause(Kind.Challenge)
        advanceUntilIdle()
        assertEquals(
            LinkedInRowState.Paused(emptySummary, "LinkedIn asked for a check, so reading is paused.", needsReconnect = false),
            deps.vm.uiState.value.linkedIn,
        )

        link.pause(Kind.RateLimited)
        advanceUntilIdle()
        assertEquals(
            "LinkedIn is asking us to slow down, so reading is paused.",
            (deps.vm.uiState.value.linkedIn as LinkedInRowState.Paused).reason,
        )

        link.pause(Kind.Unexpected)
        advanceUntilIdle()
        assertEquals(
            "Something unexpected stopped the LinkedIn read, so reading is paused.",
            (deps.vm.uiState.value.linkedIn as LinkedInRowState.Paused).reason,
        )

        link.pause(Kind.SessionExpired)
        advanceUntilIdle()
        assertEquals(
            LinkedInRowState.Paused(emptySummary, "Your LinkedIn login expired. Reconnect to keep reading.", needsReconnect = true),
            deps.vm.uiState.value.linkedIn,
        )
    }

    @Test
    fun `the summary is what the phone has stored and imported`() = runTest(dispatcher) {
        val link = FakeLinkedInLink(LinkState.Connected("me"))
        val stored = FakeLinkedInAccount()
        val deps = viewModel(link = link, stored = stored)

        stored.emit(LinkedInAccountData(LinkedInOwner("Ada Lovelace", "/p/ada.jpg"), connectionsSeen = 7, lastFetchedAtMs = 1_000L))
        advanceUntilIdle()

        assertEquals(
            LinkedInRowState.Connected(LinkedInAccountView("Ada Lovelace", "/p/ada.jpg", 7, 0, 0, emptyList(), 1_000L)),
            deps.vm.uiState.value.linkedIn,
        )
    }

    @Test
    fun `resume lifts the pause and disconnect clears the link`() = runTest(dispatcher) {
        val link = FakeLinkedInLink()
        val deps = viewModel(link = link)
        link.connect("me", consentAtMs = 1L)
        link.pause(Kind.RateLimited)
        advanceUntilIdle()

        deps.vm.onResumeLinkedIn()
        advanceUntilIdle()
        assertEquals(LinkedInRowState.Connected(emptySummary), deps.vm.uiState.value.linkedIn)

        deps.vm.onDisconnectLinkedIn()
        advanceUntilIdle()
        assertEquals(LinkedInRowState.NotConnected, deps.vm.uiState.value.linkedIn)
    }

    @Test
    fun `without a link the LinkedIn actions do nothing`() = runTest(dispatcher) {
        val deps = viewModel(link = null)

        deps.vm.onResumeLinkedIn()
        deps.vm.onDisconnectLinkedIn()
        advanceUntilIdle()

        assertEquals(null, deps.vm.uiState.value.linkedIn)
    }
}
