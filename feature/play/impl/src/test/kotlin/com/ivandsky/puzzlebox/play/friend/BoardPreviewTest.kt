package com.ivandsky.puzzlebox.play.friend

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.identity.IdentityService
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.data.repository.CustomBoardRepository
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.ComputeCustomBoard
import com.ivandsky.puzzlebox.domain.LoadSharedPuzzle
import com.ivandsky.puzzlebox.domain.SyncReceivedStatus
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.FakeGames
import com.ivandsky.puzzlebox.play.api.FriendPuzzleKey
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeCustomBoardRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The board preview on a friend's puzzle card, over the card's ViewModel with every source a
 * fake: signed in it shows the author pinned over the top three; signed out the board is never
 * read, and the author, this device's time and the sign-in prompt show; offline, the author
 * alone, with no spinner and no error; signing in from the card fills in the top three.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class BoardPreviewTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val game = TestGames.alpha
    private val fakes = FakeGames(listOf(game))
    private val code = requireNotNull(ShareCode.parse("K7Q2MX"))
    private val received = FakeReceivedPuzzleRepository()
    private val completions = FakeCompletionRepository()
    private val boards = CountingCustomBoardRepository(FakeCustomBoardRepository())

    private val me = Identity(uid = "uid-me", displayName = "Me", photoUrl = null)

    private fun puzzle() = ReceivedPuzzle(
        code = code,
        gameId = game,
        variant = VariantId("alpha/n8"),
        sharedFormatVersion = 1,
        puzzleBytes = byteArrayOf(1, 2, 3),
        title = "Crown the castle",
        authorName = "Jonas Lind",
        note = "",
        authorTimeMs = 102_000L,
        authorHints = 0,
        authorUid = "uid-jonas",
        openedAt = Instant.EPOCH,
        unpublished = false,
        postState = PostState.Pending,
        postAttempts = 0,
        difficulty = null,
    )

    private fun entry(uid: String, timeMs: Long) = LeaderboardEntry(
        uid = uid,
        displayName = uid,
        puzzleRef = "custom/${game.value}/${code.value}",
        timeMs = timeMs,
        hints = 0,
        completedAtMs = timeMs,
    )

    /** Five players on the board, plus the author's own entry, which never ranks. */
    private fun seedBoard() = runBlocking {
        (List(5) { entry("uid-${it + 1}", 60_000L + it * 1_000L) } + entry("uid-jonas", 50_000L)).forEach { boards.submit(code, it) }
    }

    private fun solveHere(timeMs: Long) {
        val ref = checkNotNull(fakes.games[game]).refOf(PuzzleKey.Custom(code.value))
        completions.setRecords(listOf(CompletionRecord(ref, timeMs, 0, RecordStats(1, "{}"))))
    }

    private fun show(identity: IdentityService) {
        runBlocking { received.upsert(puzzle()) }
        val now = object : DispatcherProvider {
            override val main = Dispatchers.Unconfined
            override val io = Dispatchers.Unconfined
            override val default = Dispatchers.Unconfined
        }
        val shared = FakeSharedPuzzleRepository()
        val viewModel = FriendPuzzleViewModel(
            key = FriendPuzzleKey(code.value),
            load = LoadSharedPuzzle(
                registry = fakes.games,
                shared = shared,
                received = received,
                budget = { Budget.within(1_000L, System::nanoTime) },
                clock = { Instant.EPOCH },
            ),
            sync = SyncReceivedStatus(shared, received),
            received = received,
            completions = completions,
            saved = FakeSavedPlayRepository(),
            games = fakes.games,
            plays = fakes.registry,
            editors = GameEditorRegistry(emptyList()),
            dispatchers = now,
            board = FriendPuzzleBoard(boards, identity, oneGoogleAccount(TOKEN), ComputeCustomBoard()),
        )
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                val state by viewModel.state.collectAsState()
                val context = LocalContext.current
                (state as? FriendCardState.Ready)?.let { ready ->
                    FriendPuzzleCard(
                        card = ready.card,
                        onAction = {},
                        onSignIn = { viewModel.signIn(context) },
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                    )
                }
            }
        }
        composeRule.waitUntil(WAIT_MS) { count("custom_board.author") > 0 }
    }

    @Test
    fun `signed in, the card shows the author pinned over the board's top three`() {
        seedBoard()
        show(FakeIdentityService(initial = me))
        composeRule.waitUntil(WAIT_MS) { count("custom_board.row") > 0 }

        node("custom_board.author").assertExists()
        assertEquals(3, count("custom_board.row"))
        assertEquals(0, count("custom_board.sign_in"))
        assertEquals(0, count("custom_board.you"))
    }

    @Test
    fun `signed out, the board is never read and the author, my local time and the sign-in prompt show`() {
        seedBoard()
        solveHere(125_000L)
        show(FakeIdentityService())

        assertEquals(0, boards.observeCalls)
        node("custom_board.author").assertExists()
        node("custom_board.you").assertExists()
        node("custom_board.sign_in").assertExists()
        assertEquals(0, count("custom_board.row"))
    }

    @Test
    fun `offline, the card shows the author row only`() {
        seedBoard()
        boards.delegate.online = false
        show(FakeIdentityService(initial = me))
        composeRule.waitUntil(WAIT_MS) { boards.observeCalls > 0 }
        composeRule.waitForIdle()

        node("custom_board.author").assertExists()
        listOf("custom_board.row", "custom_board.you", "custom_board.sign_in", "custom_board.loading", "custom_board.unavailable", "custom_board.empty")
            .forEach { assertEquals(it, 0, count(it)) }
    }

    @Test
    fun `signing in from the card fills in the top three`() {
        seedBoard()
        val identity = FakeIdentityService()
        show(identity)

        node("custom_board.sign_in").performScrollTo().performClick()
        composeRule.waitUntil(WAIT_MS) { count("custom_board.row") > 0 }

        assertEquals(listOf(TOKEN), identity.signInCalls)
        assertEquals(3, count("custom_board.row"))
        assertEquals(0, count("custom_board.sign_in"))
    }

    private fun node(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true)

    private fun count(tag: String) =
        composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().size

    /** The in-memory board, counting its reads: a signed-out card must never open one. */
    private class CountingCustomBoardRepository(val delegate: FakeCustomBoardRepository) : CustomBoardRepository by delegate {
        var observeCalls = 0
            private set

        override fun observe(gameId: GameId, code: ShareCode, order: RankOrder, limit: Int): Flow<List<LeaderboardEntry>?> {
            observeCalls++
            return delegate.observe(gameId, code, order, limit)
        }
    }

    private companion object {
        const val TOKEN = "google-token"
        const val WAIT_MS = 5_000L
    }
}
