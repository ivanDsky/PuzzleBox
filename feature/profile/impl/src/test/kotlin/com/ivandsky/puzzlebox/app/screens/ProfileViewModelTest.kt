package com.ivandsky.puzzlebox.app.screens

import android.content.Context

import com.ivandsky.puzzlebox.app.auth.GoogleIdToken
import com.ivandsky.puzzlebox.app.auth.GoogleIdToken.Unavailable.Reason.CANCELLED
import com.ivandsky.puzzlebox.app.auth.GoogleIdToken.Unavailable.Reason.MALFORMED
import com.ivandsky.puzzlebox.app.auth.GoogleIdToken.Unavailable.Reason.NETWORK
import com.ivandsky.puzzlebox.app.auth.GoogleIdToken.Unavailable.Reason.NO_CREDENTIAL
import com.ivandsky.puzzlebox.app.auth.GoogleIdToken.Unavailable.Reason.OTHER
import com.ivandsky.puzzlebox.app.auth.GoogleIdToken.Unavailable.Reason.PROVIDER_UNAVAILABLE
import com.ivandsky.puzzlebox.app.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.data.identity.AuthError
import com.ivandsky.puzzlebox.data.identity.AuthResult
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardRef
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardSnapshot
import com.ivandsky.puzzlebox.domain.GetFeaturedGame
import com.ivandsky.puzzlebox.domain.ObserveTodayLeaderboard
import com.ivandsky.puzzlebox.domain.TodayLeaderboard
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Profile ViewModel states (brief §4): signed-out/in, sign-in results, board gating. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val day = LocalDate.of(2026, 10, 31)
    private val boardRef = LeaderboardRef("zip", day)
    private val dispatcher = StandardTestDispatcher()

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
        val leaderboard: FakeLeaderboardRepository,
        val tokens: FakeTokenProvider,
    )

    private fun TestScope.viewModel(
        identity: FakeIdentityService = FakeIdentityService(initial = null),
        tokens: FakeTokenProvider = FakeTokenProvider(),
        leaderboard: FakeLeaderboardRepository = FakeLeaderboardRepository(),
    ): Deps {
        val vm = ProfileViewModel(
            identityService = identity,
            idTokenProvider = tokens,
            observeTodayLeaderboard = ObserveTodayLeaderboard(
                identity = identity,
                leaderboard = leaderboard,
                // Today's board is the featured game's: the registry's first, "zip".
                featured = GetFeaturedGame(TestGames.registry(listOf(GameId("zip")))),
                today = { day },
            ),
        )
        // The state is observed only while subscribed (the route's lifecycle collector).
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        return Deps(vm, identity, leaderboard, tokens)
    }

    private val Deps.signedOut: ProfileUiState.SignedOut
        get() = vm.uiState.value as ProfileUiState.SignedOut

    private val Deps.signedIn: ProfileUiState.SignedIn
        get() = vm.uiState.value as ProfileUiState.SignedIn

    // Plain JVM test: the fake provider ignores the context, so a mock satisfies the seam.
    private val appContext: Context = org.mockito.Mockito.mock(Context::class.java)

    @Test
    fun `signed out is the initial state, board not observed (A7-A15)`() = runTest(dispatcher) {
        val deps = viewModel()

        // Signed out carries no board (nothing loading) and no sign-in progress.
        assertEquals(ProfileUiState.SignedOut(), deps.vm.uiState.value)
        assertTrue(deps.leaderboard.observedBoards.isEmpty())
    }

    @Test
    fun `successful sign-in sets the identity and starts the today board`() = runTest(dispatcher) {
        val deps = viewModel()

        deps.vm.onSignIn(appContext)
        advanceUntilIdle()

        // Signed in: the identity shows and no sign-in message is carried.
        assertEquals(
            Identity(uid = "fake-uid", displayName = "Fake Player", photoUrl = null),
            deps.signedIn.identity,
        )
        assertEquals(listOf(boardRef), deps.leaderboard.observedBoards)
        assertEquals(deps.identity.currentIdentity, deps.signedIn.identity)
    }

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
            assertTrue(deps.vm.uiState.value is ProfileUiState.SignedOut)
            assertTrue(deps.leaderboard.observedBoards.isEmpty())
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
    fun `sign-out clears identity and drops the board`() = runTest(dispatcher) {
        val deps = viewModel(identity = FakeIdentityService(initial = Identity("u1", "Ann", null)))
        advanceUntilIdle()
        assertTrue(deps.vm.uiState.value is ProfileUiState.SignedIn)

        deps.vm.onSignOut()
        advanceUntilIdle()

        // Signed out: no identity and no board.
        assertTrue(deps.vm.uiState.value is ProfileUiState.SignedOut)
        assertEquals(1, deps.identity.signOutCalls.size)
    }

    @Test
    fun `board entries flow into the state`() = runTest(dispatcher) {
        val deps = viewModel(
            identity = FakeIdentityService(initial = Identity("u1", "Ann", null)),
            leaderboard = FakeLeaderboardRepository(),
        )

        deps.leaderboard.setEntries(
            boardRef,
            listOf(
                LeaderboardEntry("u1", "Ann", "zip/2026-10-31/592", 95_000, 2, 1_700_000_000_000),
                LeaderboardEntry("u2", "Bo", "zip/2026-10-31/592", 80_000, 0, 1_700_000_000_000),
            ),
        )
        advanceUntilIdle()

        val board = deps.signedIn.board
        assertTrue(board is TodayLeaderboard.Board.Loaded)
        val snapshot: LeaderboardSnapshot = (board as TodayLeaderboard.Board.Loaded).snapshot
        assertEquals(LeaderboardSnapshot.FORMAT_VERSION, snapshot.formatVersion)
        assertEquals(listOf("Bo", "Ann"), snapshot.entries.map { it.displayName })
    }
}
