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
import com.ivandsky.puzzlebox.domain.ComputeStreak
import com.ivandsky.puzzlebox.domain.GetFeaturedGame
import com.ivandsky.puzzlebox.domain.GetPuzzleDay
import com.ivandsky.puzzlebox.domain.ObserveGameHistory
import com.ivandsky.puzzlebox.domain.ObserveGameStats
import com.ivandsky.puzzlebox.domain.ObserveTodayLeaderboard
import com.ivandsky.puzzlebox.domain.TodayLeaderboard
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.game.ScoreModel
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.ThemeMode
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeLeaderboardRepository
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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

/** Profile ViewModel states (brief §4, A20, N4): the record, signed-out/in, sign-in results, board gating, theme. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val day = LocalDate.of(2026, 10, 31)
    private val boardRef = LeaderboardRef("zip", day)
    private val dispatcher = StandardTestDispatcher()

    private val zip = GameId("zip")
    private val tango = GameId("tango")

    // Today's board is the featured game's: the registry's first, "zip". Tango ranks hints first.
    private val registry = GameRegistry(listOf(TestGames.plugin(zip), hintsFirst(TestGames.plugin(tango))))
    private val featured = GetFeaturedGame(registry)
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
        val leaderboard: FakeLeaderboardRepository,
        val tokens: FakeTokenProvider,
        val preferences: FakeUserPreferencesRepository,
    )

    private fun TestScope.viewModel(
        identity: FakeIdentityService = FakeIdentityService(initial = null),
        tokens: FakeTokenProvider = FakeTokenProvider(),
        leaderboard: FakeLeaderboardRepository = FakeLeaderboardRepository(),
        completions: FakeCompletionRepository = FakeCompletionRepository(),
        preferences: FakeUserPreferencesRepository = FakeUserPreferencesRepository(),
    ): Deps {
        val history = ObserveGameHistory(registry, FakeDailyRepository(), completions)
        val vm = ProfileViewModel(
            identityService = identity,
            idTokenProvider = tokens,
            observeTodayLeaderboard = ObserveTodayLeaderboard(
                leaderboard = ObserveLeaderboard(identity, leaderboard),
                featured = featured,
                today = { day },
            ),
            observeGameStats = ObserveGameStats(registry, history, ComputeStreak(GetPuzzleDay(registry, clock))),
            observeGameHistory = history,
            registry = registry,
            brands = GamePlayRegistry(emptyList()),
            preferences = preferences,
        )
        // The state is observed only while subscribed (the route's lifecycle collector).
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        return Deps(vm, identity, leaderboard, tokens, preferences)
    }

    private val Deps.signedOut: ProfileUiState.Account.SignedOut
        get() = vm.uiState.value.account as ProfileUiState.Account.SignedOut

    private val Deps.signedIn: ProfileUiState.Account.SignedIn
        get() = vm.uiState.value.account as ProfileUiState.Account.SignedIn

    // Plain JVM test: the fake provider ignores the context, so a mock satisfies the seam.
    private val appContext: Context = org.mockito.Mockito.mock(Context::class.java)

    @Test
    fun `signed out is the initial state, board not observed (A7-A15)`() = runTest(dispatcher) {
        val deps = viewModel()

        // Signed out carries no board (nothing loading) and no sign-in progress.
        assertEquals(ProfileUiState.Account.SignedOut(), deps.vm.uiState.value.account)
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

    @Test
    fun `the record carries lifetime stats, days played and one row per game - A20`() = runTest(dispatcher) {
        val completions = FakeCompletionRepository(
            listOf(
                record(zip, day, timeMs = 95_000, hints = 2),
                record(zip, day.minusDays(1), timeMs = 80_000, hints = 0),
                record(tango, day, timeMs = 61_000, hints = 0),
                // Practice (no edition number) never counts.
                record(tango, day.minusDays(5), timeMs = 10_000, hints = 0, puzzleNumber = null),
            ),
        )

        val state = viewModel(completions = completions).vm.uiState.value

        assertEquals(3, state.stats.solved)
        assertEquals(2, state.stats.hintFree)
        assertEquals(2, state.stats.bestStreak)
        assertEquals(day.minusDays(1), state.stats.playingSince)
        assertEquals(2, state.daysPlayed)
        assertEquals(listOf(zip, tango), state.games.map { it.gameId })
        assertEquals(listOf(2, 1), state.games.map { it.solved })
        assertEquals(listOf(BestScore(80_000, 0), BestScore(61_000, 0)), state.games.map { it.best })
    }

    @Test
    fun `a hints-ranked game's best is its fewest-hints solve - A10`() = runTest(dispatcher) {
        val completions = FakeCompletionRepository(
            listOf(
                record(zip, day, timeMs = 30_000, hints = 2),
                record(zip, day.minusDays(1), timeMs = 70_000, hints = 0),
                record(tango, day, timeMs = 30_000, hints = 2),
                record(tango, day.minusDays(1), timeMs = 70_000, hints = 0),
            ),
        )

        val games = viewModel(completions = completions).vm.uiState.value.games.associateBy { it.gameId }

        assertEquals(RankOrder.HintsThenTime, games.getValue(tango).rankOrder)
        assertEquals(BestScore(70_000, 0), games.getValue(tango).best)
        assertEquals(RankOrder.TimeThenHints, games.getValue(zip).rankOrder)
        assertEquals(BestScore(30_000, 2), games.getValue(zip).best)
    }

    private fun <P> hintsFirst(
        plugin: GamePlugin<P, TestGames.Session, TestGames.Model>,
    ): GamePlugin<P, TestGames.Session, TestGames.Model> = GamePlugin(
        definition = plugin.definition,
        rules = plugin.rules,
        session = plugin.session,
        score = object : ScoreModel<TestGames.Session> {
            override fun completion(session: TestGames.Session) = plugin.score.completion(session)
            override val rankOrder = RankOrder.HintsThenTime
        },
        stateOf = plugin.stateOf,
        initialSession = plugin.initialSession,
        refOf = plugin.refOf,
        replaySession = plugin.replaySession,
        rendererModel = plugin.rendererModel,
    )

    @Test
    fun `choosing dark persists the theme - N4`() = runTest(dispatcher) {
        val deps = viewModel()
        assertEquals(ThemeMode.SYSTEM, deps.vm.uiState.value.themeMode)

        deps.vm.onSetThemeMode(ThemeMode.DARK)
        advanceUntilIdle()

        assertEquals(ThemeMode.DARK, deps.preferences.themeMode.first())
        assertEquals(ThemeMode.DARK, deps.vm.uiState.value.themeMode)
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
        assertTrue(deps.vm.uiState.value.account is ProfileUiState.Account.SignedIn)

        deps.vm.onSignOut()
        advanceUntilIdle()

        // Signed out: no identity and no board.
        assertTrue(deps.vm.uiState.value.account is ProfileUiState.Account.SignedOut)
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
