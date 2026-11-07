package com.ivandsky.puzzlebox.play.friend

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.ivandsky.puzzlebox.auth.GoogleIdTokenProvider
import com.ivandsky.puzzlebox.common.di.commonModule
import com.ivandsky.puzzlebox.data.calendar.GameCalendar
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.data.repository.CompletionRepository
import com.ivandsky.puzzlebox.data.repository.CustomBoardRepository
import com.ivandsky.puzzlebox.data.repository.DailyRepository
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.SavedPlayRepository
import com.ivandsky.puzzlebox.data.repository.SharedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.UserPreferencesRepository
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.AuthorPin
import com.ivandsky.puzzlebox.domain.ComputeCustomBoard
import com.ivandsky.puzzlebox.domain.CustomBoardView
import com.ivandsky.puzzlebox.domain.LoadPastDay
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.FakeGames
import com.ivandsky.puzzlebox.play.api.CustomResultKey
import com.ivandsky.puzzlebox.play.di.playModule
import com.ivandsky.puzzlebox.testing.calendar.FakeGameCalendar
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeCustomBoardRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDailyRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The per-puzzle board on a friend puzzle's result: ranked, it shows five rows, a gap and my
 * row below the cut; signed out it offers sign-in; unreadable it says so; a result that will
 * never post reads Not posted. Signing in with a Google token loads the ranked board in place.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CustomBoardTest {

    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val composeRule = createComposeRule()

    private val author = AuthorPin(uid = "uid-jonas", name = "Jonas Lind", timeMs = 102_000L, hints = 0)
    private val code = checkNotNull(ShareCode.parse("K7Q2MX"))
    private val alpha = TestGames.alpha

    private var koin: KoinApplication? = null
    private val screen = CoroutineScope(main.testDispatcher + Job())

    @After
    fun tearDown() {
        screen.cancel()
        koin?.close()
    }

    private fun entry(uid: String, timeMs: Long) = LeaderboardEntry(
        uid = uid,
        displayName = uid,
        puzzleRef = "custom/${alpha.value}/${code.value}",
        timeMs = timeMs,
        hints = 0,
        completedAtMs = timeMs,
    )

    private fun players(count: Int) = List(count) { entry("uid-${it + 1}", 60_000L + it * 1_000L) }

    private fun show(
        board: CustomBoardView?,
        postState: PostState? = PostState.Pending,
        signIn: BoardSignIn = BoardSignIn.Idle,
        closed: Boolean = false,
    ) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                CustomBoard(
                    board = board,
                    author = author,
                    yours = BestScore(118_000L, 0),
                    postState = postState,
                    rankOrder = RankOrder.TimeThenHints,
                    signIn = signIn,
                    onSignIn = {},
                    closed = closed,
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `a ranked board shows the author pinned, the top five, a gap and my row below the cut`() {
        show(ComputeCustomBoard()(players(7), myUid = "uid-7", author = author, limit = 5))

        node("custom_board.author").assertExists()
        assertEquals(5, count("custom_board.row"))
        node("custom_board.gap").assertExists()
        node("custom_board.row.me").assertExists()
        assertEquals("my ranked row stands in for the unposted one", 0, count("custom_board.you"))
        assertEquals(0, count("custom_board.sign_in"))
    }

    @Test
    fun `my row inside the top five needs no gap`() {
        show(ComputeCustomBoard()(players(7), myUid = "uid-2", author = author, limit = 5))

        assertEquals(4, count("custom_board.row"))
        node("custom_board.row.me").assertExists()
        assertEquals(0, count("custom_board.gap"))
    }

    @Test
    fun `signed out keeps the author and my time, and offers sign-in`() {
        show(CustomBoardView.SignedOut(author))

        node("custom_board.author").assertExists()
        node("custom_board.you").assertExists()
        node("custom_board.sign_in").assertExists()
        assertEquals(0, count("custom_board.row"))
    }

    @Test
    fun `a sign-in in flight is not offered twice`() {
        show(CustomBoardView.SignedOut(author), signIn = BoardSignIn.SigningIn)

        node("custom_board.sign_in").assertIsNotEnabled()
    }

    @Test
    fun `a failed sign-in says so beside the button`() {
        show(CustomBoardView.SignedOut(author), signIn = BoardSignIn.Failed)

        node("custom_board.sign_in.failed").assertExists()
    }

    @Test
    fun `an unreadable board reads unavailable and asks for nothing`() {
        show(CustomBoardView.Unavailable(author))

        node("custom_board.author").assertExists()
        node("custom_board.unavailable").assertExists()
        assertEquals(0, count("custom_board.sign_in"))
    }

    @Test
    fun `the first read shows the loading state under the author already known`() {
        show(board = null)

        node("custom_board.loading").assertExists()
        node("custom_board.author").assertExists()
    }

    @Test
    fun `a result that will never post reads Not posted on my row`() {
        show(ComputeCustomBoard()(players(3), myUid = "uid-me", author = author, limit = 5), postState = PostState.LocalOnly)

        node("custom_board.you").assertExists()
        node("custom_board.you.local_only").assertExists()
    }

    @Test
    fun `a closed board still lists its players, reads closed and leaves my time unposted`() {
        show(ComputeCustomBoard()(players(3), myUid = "uid-me", author = author, limit = 5), closed = true)

        assertEquals(3, count("custom_board.row"))
        node("custom_board.closed").assertExists()
        node("custom_board.you.local_only").assertExists()
        assertEquals(0, count("custom_board.you.pending"))
    }

    @Test
    fun `a closed board my best is already on keeps it Posted`() {
        show(ComputeCustomBoard()(players(3), myUid = "uid-me", author = author, limit = 5), postState = PostState.Posted, closed = true)

        node("custom_board.closed").assertExists()
        node("custom_board.you.posted").assertExists()
    }

    @Test
    fun `signed out, a closed board offers no sign-in`() {
        show(CustomBoardView.SignedOut(author), closed = true)

        node("custom_board.author").assertExists()
        node("custom_board.closed").assertExists()
        node("custom_board.you.local_only").assertExists()
        assertEquals(0, count("custom_board.sign_in"))
    }

    @Test
    fun `offline, a closed board reads closed and promises no later post`() {
        show(CustomBoardView.Unavailable(author), closed = true)

        node("custom_board.closed").assertExists()
        assertEquals(0, count("custom_board.unavailable"))
        assertEquals(0, count("custom_board.you.pending"))
    }

    @Test
    fun `signing in with a Google token loads the ranked board`() {
        val identity = FakeIdentityService()
        val boards = FakeCustomBoardRepository()
        runBlocking { (players(6) + entry(FAKE_UID, 200_000L)).forEach { boards.submit(code, it) } }
        // The board's rules deny signed-out reads; the fake follows the signed-in user.
        screen.launch { identity.current.collect { boards.signedIn = it != null } }
        startGraph(identity, boards, oneGoogleAccount(TOKEN))

        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                CustomResultRoute(key = CustomResultKey(alpha.value, code.value, 200_000L, 0), onPlayAgain = {}, onDone = {})
            }
        }
        composeRule.waitUntil(WAIT_MS) { count("custom_board.sign_in") > 0 }

        node("custom_board.sign_in").performScrollTo().performClick()
        composeRule.waitUntil(WAIT_MS) { count("custom_board.row.me") > 0 }

        assertEquals(listOf(TOKEN), identity.signInCalls)
        assertEquals(5, count("custom_board.row"))
        node("custom_board.gap").assertExists()
        assertEquals(0, count("custom_board.sign_in"))
    }

    private fun startGraph(identity: IdentityService, boards: CustomBoardRepository, tokens: GoogleIdTokenProvider) {
        val fakes = FakeGames(listOf(alpha))
        val received = FakeReceivedPuzzleRepository()
        runBlocking { received.upsert(puzzle()) }
        stopKoin() // tests share the JVM-global Koin context
        koin = startKoin {
            modules(
                commonModule,
                module {
                    single<DailyRepository> { FakeDailyRepository() }
                    single<CompletionRepository> { FakeCompletionRepository() }
                    single<GameRegistry> { fakes.games }
                    single<GamePlayRegistry> { fakes.registry }
                    single<SavedPlayRepository> { FakeSavedPlayRepository() }
                    single<ReceivedPuzzleRepository> { received }
                    single<SharedPuzzleRepository> { FakeSharedPuzzleRepository() }
                    single<GameCalendar> { FakeGameCalendar(LocalDate.parse("2026-11-04")) }
                    single<UserPreferencesRepository> { FakeUserPreferencesRepository() }
                    single<CustomBoardRepository> { boards }
                    single<IdentityService> { identity }
                    single<GoogleIdTokenProvider> { tokens }
                    single { GameEditorRegistry(emptyList()) }
                    factory { LoadPastDay(get(), get()) }
                },
                playModule,
            )
        }
    }

    private fun puzzle() = ReceivedPuzzle(
        code = code,
        gameId = alpha,
        variant = TestGames.variant(alpha),
        sharedFormatVersion = 1,
        puzzleBytes = TestGames.puzzle(alpha).payload,
        title = "Crown the castle",
        authorName = author.name,
        note = "",
        authorTimeMs = author.timeMs,
        authorHints = author.hints,
        authorUid = author.uid,
        openedAt = Instant.ofEpochMilli(1_000L),
        unpublished = false,
        postState = PostState.Pending,
        postAttempts = 0,
        difficulty = null,
    )

    private fun node(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true)

    private fun count(tag: String) =
        composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().size

    private companion object {
        const val TOKEN = "google-token"

        /** The uid [FakeIdentityService] signs in as. */
        const val FAKE_UID = "fake-uid"
        const val WAIT_MS = 5_000L
    }
}
