package com.ivandsky.puzzlebox.app.screens

import android.content.Context

import com.ivandsky.puzzlebox.app.auth.GoogleIdToken
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

    @Test
    fun `unavailable token degrades to a message, never throws (A7)`() = runTest(dispatcher) {
        val deps = viewModel(tokens = FakeTokenProvider(next = GoogleIdToken.Unavailable))

        deps.vm.signIn(appContext)
        advanceUntilIdle()

        assertEquals(1, deps.tokens.calls.size)
        assertTrue(deps.vm.state.value.signInMessage!!.contains("unavailable"))
        assertNull(deps.vm.state.value.identity)
        assertTrue(deps.leaderboard.observedBoards.isEmpty())
    }

    @Test
    fun `backend rejection surfaces a friendly message`() = runTest(dispatcher) {
        val identity = FakeIdentityService(
            initial = null,
            signInResult = AuthResult.Err(AuthError.Rejected("bad token")),
        )
        val deps = viewModel(identity = identity)

        deps.vm.signIn(appContext)
        advanceUntilIdle()

        assertTrue(deps.vm.state.value.signInMessage!!.contains("refused"))
        assertNull(deps.vm.state.value.identity)
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
