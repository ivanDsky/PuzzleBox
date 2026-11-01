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
import com.ivandsky.puzzlebox.data.testing.FakeIdentityService
import com.ivandsky.puzzlebox.data.testing.FakeLeaderboardRepository
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Profile ViewModel states (brief §4): signed-out/in, sign-in results, board gating. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val day = LocalDate.of(2026, 10, 31)
    private val boardRef = LeaderboardRef("zip", day)
    private val dispatcher = StandardTestDispatcher()

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
            leaderboard = leaderboard,
            todayBoard = { boardRef },
        )
        advanceUntilIdle()
        return Deps(vm, identity, leaderboard, tokens)
    }

    // Plain JVM test: the fake provider ignores the context, so a mock satisfies the seam.
    private val appContext: Context = org.mockito.Mockito.mock(Context::class.java)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `signed out is the initial state, board not observed (A7-A15)`() = runTest(dispatcher) {
        val deps = viewModel()

        assertNull(deps.vm.state.value.identity)
        assertTrue(deps.leaderboard.observedBoards.isEmpty())
        assertTrue(!deps.vm.state.value.boardLoading)
    }

    @Test
    fun `successful sign-in sets the identity and starts the today board`() = runTest(dispatcher) {
        val deps = viewModel()

        deps.vm.signIn(appContext)
        advanceUntilIdle()

        assertEquals(
            Identity(uid = "fake-uid", displayName = "Fake Player", photoUrl = null),
            deps.vm.state.value.identity,
        )
        assertNull(deps.vm.state.value.signInMessage)
        assertEquals(listOf(boardRef), deps.leaderboard.observedBoards)
        assertEquals(deps.identity.currentIdentity, deps.vm.state.value.identity)
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
        deps.vm.signIn(appContext)
        advanceUntilIdle()
        return requireNotNull(deps.vm.state.value.signInMessage)
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

            deps.vm.signIn(appContext)
            advanceUntilIdle()

            assertEquals(
                "Google sign-in isn't available right now. Try again, or try another account.",
                deps.vm.state.value.signInMessage,
            )
            assertNull(deps.vm.state.value.identity)
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
        assertTrue(deps.vm.state.value.identity != null)

        deps.vm.signOut()
        advanceUntilIdle()

        assertNull(deps.vm.state.value.identity)
        assertEquals(1, deps.identity.signOutCalls.size)
        assertNull(deps.vm.state.value.board)
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

        val snapshot = deps.vm.state.value.board!!
        assertEquals(LeaderboardSnapshot.FORMAT_VERSION, snapshot.formatVersion)
        assertEquals(listOf("Bo", "Ann"), snapshot.entries.map { it.displayName })
    }
}
