package com.ivandsky.puzzlebox.play.friend

import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.leaderboard.LeaderboardEntry
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.data.repository.CustomBoardRepository
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.domain.ComputeCustomBoard
import com.ivandsky.puzzlebox.domain.CustomBoardView
import com.ivandsky.puzzlebox.domain.LoadSharedPuzzle
import com.ivandsky.puzzlebox.domain.OpenOutcome
import com.ivandsky.puzzlebox.domain.SyncReceivedStatus
import com.ivandsky.puzzlebox.game.BestScore
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RankOrder
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.FakeGames
import com.ivandsky.puzzlebox.play.api.FriendPuzzleKey
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeIdentityService
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import com.ivandsky.puzzlebox.testing.rule.MainDispatcherRule
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The state a friend's puzzle card reads, over fakes: Loading until the code has been tried;
 * each way it can fail to open as its own state, and Retry opening it again; and, once open,
 * the primary action as fresh, saved or solved here. A signed-out card never reads its board.
 */
class FriendPuzzleViewModelTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val game = TestGames.alpha
    private val fakes = FakeGames(listOf(game))
    private val code = checkNotNull(ShareCode.parse("K7Q2MX"))
    private val backend = FakeSharedPuzzleRepository().apply { signedInUid = AUTHOR }
    private val received = FakeReceivedPuzzleRepository()
    private val completions = FakeCompletionRepository()
    private val saved = FakeSavedPlayRepository()
    private var boardReads = 0

    /** A board that counts its reads; one that is never read stays at zero. */
    private val boards = object : CustomBoardRepository {
        override fun observe(gameId: GameId, code: ShareCode, order: RankOrder, limit: Int): Flow<List<LeaderboardEntry>?> {
            boardReads++
            return emptyFlow()
        }

        override suspend fun submit(code: ShareCode, entry: LeaderboardEntry) = error("not under test")

        override suspend fun solvesCount(code: ShareCode): Int? = error("not under test")
    }

    /** The open waits on [openGate], when it is set: the code is not yet tried. */
    private var openGate: CompletableDeferred<Unit>? = null
    private val receivedRows = object : ReceivedPuzzleRepository by received {
        override suspend fun get(code: ShareCode): ReceivedPuzzle? {
            openGate?.await()
            return received.get(code)
        }
    }

    @Test
    fun `the card is loading until the code has been tried, then ready`() = runTest {
        openGate = CompletableDeferred()
        received.upsert(stored())
        val vm = viewModel()

        assertEquals(FriendCardState.Loading, vm.state.value)

        openGate?.complete(Unit)

        assertTrue(vm.state.value is FriendCardState.Ready)
    }

    @Test
    fun `an unknown code is not found`() = runTest {
        assertEquals(FriendCardState.Failed(OpenOutcome.NotFound), viewModel().state.value)
    }

    @Test
    fun `a code that is not a share code is not found`() = runTest {
        val vm = viewModel(FriendPuzzleKey("not a code"))

        assertEquals(FriendCardState.Failed(OpenOutcome.NotFound), vm.state.value)
    }

    @Test
    fun `an offline open fails as offline, and retry opens it once the puzzle is on the device`() = runTest {
        backend.online = false
        val vm = viewModel()

        assertEquals(FriendCardState.Failed(OpenOutcome.Offline), vm.state.value)

        received.upsert(stored())
        vm.retry()

        assertTrue(vm.state.value is FriendCardState.Ready)
    }

    @Test
    fun `a puzzle withdrawn before it was opened is no longer available`() = runTest {
        publish()
        backend.unpublish(code)

        assertEquals(FriendCardState.Failed(OpenOutcome.NoLongerAvailable), viewModel().state.value)
    }

    @Test
    fun `a puzzle in a newer format needs an update`() = runTest {
        publish(formatVersion = SharedPuzzleDoc.FORMAT_VERSION + 1)

        assertEquals(
            FriendCardState.Failed(OpenOutcome.NeedsUpdate(SharedPuzzleDoc.FORMAT_VERSION + 1)),
            viewModel().state.value,
        )
    }

    @Test
    fun `a puzzle of a game this build lacks is unknown`() = runTest {
        publish(gameId = "ghost")

        assertEquals(FriendCardState.Failed(OpenOutcome.UnknownGame("ghost")), viewModel().state.value)
    }

    @Test
    fun `a puzzle never played here is Play with no solve of yours`() = runTest {
        received.upsert(stored())

        val card = readyCard(viewModel())

        assertEquals(FriendCardAction.Play, card.action)
        assertEquals(null, card.yours)
    }

    @Test
    fun `a saved play is Resume at its elapsed time`() = runTest {
        received.upsert(stored())
        saved.save(slot(), byteArrayOf(1), elapsedMs = 41_000L)

        assertEquals(FriendCardAction.Resume(41_000L), readyCard(viewModel()).action)
    }

    @Test
    fun `a solve with nothing saved is Replay, carrying the solve that stands`() = runTest {
        received.upsert(stored())
        completions.setRecords(listOf(solve(timeMs = 90_000L, hints = 1, isRecord = true)))

        val card = readyCard(viewModel())

        assertEquals(FriendCardAction.Replay, card.action)
        assertEquals(BestScore(90_000L, 1), card.yours)
    }

    @Test
    fun `a saved play after a solve is Resume, not Replay`() = runTest {
        received.upsert(stored())
        completions.setRecords(listOf(solve(timeMs = 90_000L, hints = 0, isRecord = true)))
        saved.save(slot(), byteArrayOf(1), elapsedMs = 5_000L)

        assertEquals(FriendCardAction.Resume(5_000L), readyCard(viewModel()).action)
    }

    @Test
    fun `signed out, the board is signed out and its rows are never read`() = runTest {
        received.upsert(stored())

        val card = readyCard(viewModel())

        assertTrue(card.board is CustomBoardView.SignedOut)
        assertEquals("no board read while signed out", 0, boardReads)
    }

    private fun TestScope.viewModel(key: FriendPuzzleKey = FriendPuzzleKey(code.value)): FriendPuzzleViewModel {
        val testDispatcher = main.testDispatcher
        val now = object : DispatcherProvider {
            override val main = testDispatcher
            override val io = testDispatcher
            override val default = testDispatcher
        }
        val vm = FriendPuzzleViewModel(
            key = key,
            load = LoadSharedPuzzle(
                registry = fakes.games,
                shared = backend,
                received = receivedRows,
                budget = { Budget.within(1_000L, System::nanoTime) },
                clock = { Instant.EPOCH },
            ),
            sync = SyncReceivedStatus(backend, received),
            received = receivedRows,
            completions = completions,
            saved = saved,
            games = fakes.games,
            plays = fakes.registry,
            editors = GameEditorRegistry(emptyList()),
            dispatchers = now,
            board = FriendPuzzleBoard(boards, FakeIdentityService(), dismissedGoogleSignIn, ComputeCustomBoard()),
        )
        backgroundScope.launch(testDispatcher) { vm.state.collect {} }
        return vm
    }

    private fun readyCard(vm: FriendPuzzleViewModel): FriendCard =
        (vm.state.value as FriendCardState.Ready).card

    private fun slot() = PlaySlot.Custom(game, code.value)

    private fun solve(timeMs: Long, hints: Int, isRecord: Boolean) = CompletionRecord(
        checkNotNull(fakes.games[game]).refOf(PuzzleKey.Custom(code.value)),
        timeMs,
        hints,
        RecordStats(1, "{}"),
        isRecord = isRecord,
    )

    private suspend fun publish(gameId: String = game.value, formatVersion: Int = SharedPuzzleDoc.FORMAT_VERSION) {
        backend.publish(
            SharedPuzzleDoc(
                code = code, formatVersion = formatVersion, gameId = gameId, variant = "alpha/n8",
                shareCodecVersion = 1, payload = byteArrayOf(1, 2, 3), title = "Crown the castle",
                authorName = "Jonas", note = "", authorUid = AUTHOR, authorTimeMs = 102_000L, authorHints = 0,
                createdAtMs = 0L, unpublished = false,
            ),
        )
    }

    private fun stored() = ReceivedPuzzle(
        code = code, gameId = game, variant = VariantId("alpha/n8"), sharedFormatVersion = 1,
        puzzleBytes = byteArrayOf(1, 2, 3), title = "Crown the castle", authorName = "Jonas", note = "",
        authorTimeMs = 102_000L, authorHints = 0, authorUid = AUTHOR, openedAt = Instant.EPOCH,
        unpublished = false, postState = PostState.Pending, postAttempts = 0, difficulty = null,
    )

    private companion object {
        const val AUTHOR = "uid-jonas"
    }
}
