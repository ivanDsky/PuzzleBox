package com.ivandsky.puzzlebox.play.friend

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.toSize
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.LoadSharedPuzzle
import com.ivandsky.puzzlebox.play.domain.ReportPuzzle
import com.ivandsky.puzzlebox.play.domain.SyncReceivedStatus
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.gameui.replay.GameReplayBoard
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.FakeGames
import com.ivandsky.puzzlebox.play.GOLDEN_TABLET
import com.ivandsky.puzzlebox.play.api.CustomGameKey
import com.ivandsky.puzzlebox.play.api.FriendPuzzleKey
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeHiddenPuzzles
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The friend page's hidden board (nav-rethink M12-8), on the route over its real view model and fakes: the puzzle's
 * board sits between the note and the chips, blurred under "Hidden until you play" until you have solved it and
 * sharp after. The page's other parts (the action, Report, the ranks) stay beside it, and while the code opens the
 * quiet bars take the board's slot.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class FriendHiddenBoardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val game = TestGames.alpha
    private val fakes = FakeGames(listOf(game))
    private val stub = StubGivensBoard()
    private val code = requireNotNull(ShareCode.parse("K7Q2MX"))
    private val shared = FakeSharedPuzzleRepository().apply { signedInUid = REPORTER }
    private val received = FakeReceivedPuzzleRepository()
    private val completions = FakeCompletionRepository()
    private val played = mutableListOf<CustomGameKey>()
    private var openGate: CompletableDeferred<Unit>? = null

    @Test
    fun `an unsolved puzzle shows its board hidden and not the sharp one`() {
        show()

        node("friend.card.board.hidden").assertIsDisplayed().assert(hasContentDescription("Board hidden until you play"))
        assertEquals(0, count("friend.card.board.sharp"))
        assertEquals(listOf(stored().asPuzzle()), stub.asked.distinct())
    }

    @Test
    fun `a solved puzzle shows the sharp board and not the hidden one`() {
        solved()
        show()

        node("friend.card.board.sharp").assertIsDisplayed().assert(hasContentDescription("Board"))
        assertEquals(0, count("friend.card.board.hidden"))
    }

    @Test
    @Config(sdk = [34], qualifiers = GOLDEN_TABLET)
    fun `on a tablet the Community right pane draws the hidden board too, with no Up bar`() {
        show(showUp = false)

        node("friend.card.board.hidden").assertIsDisplayed()
        assertEquals(0, count("friend.up"))
        node("friend.card.play").assertExists()
    }

    @Test
    fun `the board sits between the note and the chips`() {
        show()

        val note = bounds("friend.card.note")
        val board = bounds("friend.card.board.hidden")
        val chips = bounds("friend.card.game")
        assertTrue("board under the note", board.top >= note.bottom)
        assertTrue("chips under the board", chips.top >= board.bottom)
    }

    @Test
    fun `the page keeps its action, its facts, its ranks and Report beside the board`() {
        show()

        node("friend.card.title").assertIsDisplayed()
        node("friend.card.author").assertExists()
        node("friend.card.note").assertExists()
        node("friend.card.size").assertExists()
        node("friend.card.author.time").assertExists()
        node("friend.card.play").performScrollTo().performClick()
        assertEquals(listOf(CustomGameKey(game.value, code.value)), played)

        node("friend.card.more").performScrollTo().performClick()
        node("friend.card.report").assertIsDisplayed()
    }

    @Test
    fun `a puzzle whose game draws no such board shows none`() {
        show(board = null)

        node("friend.card.title").assertIsDisplayed()
        assertEquals(0, count("friend.card.board.hidden"))
    }

    @Test
    fun `the quiet bars hold the board's slot while the code opens`() {
        openGate = CompletableDeferred()
        show()

        node("friend.card.loading.board").assertIsDisplayed()

        openGate?.complete(Unit)
        composeRule.waitForIdle()

        node("friend.card.board.hidden").assertIsDisplayed()
        assertEquals(0, count("friend.card.loading.board"))
    }

    private fun solved() {
        val ref = checkNotNull(fakes.games[game]).refOf(PuzzleKey.Custom(code.value))
        completions.setRecords(listOf(CompletionRecord(ref, 125_000L, 0, RecordStats(1, "{}"))))
    }

    private fun show(board: GameReplayBoard? = stub, showUp: Boolean = true) {
        fakes[game].replayBoard = board
        runBlocking { received.upsert(stored()) }
        val now = object : DispatcherProvider {
            override val main = Dispatchers.Unconfined
            override val io = Dispatchers.Unconfined
            override val default = Dispatchers.Unconfined
        }
        val gated = object : ReceivedPuzzleRepository by received {
            override suspend fun get(code: ShareCode): ReceivedPuzzle? {
                openGate?.await()
                return received.get(code)
            }
        }
        val viewModel = FriendPuzzleViewModel(
            key = FriendPuzzleKey(code.value),
            load = LoadSharedPuzzle(
                registry = fakes.games,
                shared = shared,
                received = gated,
                budget = { Budget.within(1_000L, System::nanoTime) },
                clock = { Instant.EPOCH },
            ),
            sync = SyncReceivedStatus(shared, received),
            received = gated,
            completions = completions,
            saved = FakeSavedPlayRepository(),
            games = fakes.games,
            plays = fakes.registry,
            editors = GameEditorRegistry(emptyList()),
            dispatchers = now,
            board = signedOutBoard(),
            reportPuzzle = ReportPuzzle(shared, FakeHiddenPuzzles()),
        )
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                FriendPuzzleRoute(key = FriendPuzzleKey(code.value), onPlay = { played += it }, onBack = {}, showUp = showUp, viewModel = viewModel)
            }
        }
        composeRule.waitForIdle()
    }

    private fun node(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true)

    private fun count(tag: String) = composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().size

    /** [tag]'s place in the scrolled content, not clipped to the screen: a tall page has parts off it. */
    private fun bounds(tag: String): Rect {
        val node = node(tag).fetchSemanticsNode()
        return Rect(node.positionInRoot, node.size.toSize())
    }

    private fun ReceivedPuzzle.asPuzzle() = Puzzle(gameId, variant, sharedFormatVersion, puzzleBytes)

    private fun stored() = ReceivedPuzzle(
        code = code, gameId = game, variant = VariantId("alpha/n8"), sharedFormatVersion = 1,
        puzzleBytes = byteArrayOf(1, 2, 3), title = "Crown the castle", authorName = "Jonas",
        note = "The corner is the trick", authorTimeMs = 102_000L, authorHints = 0, authorUid = "uid-jonas",
        openedAt = Instant.EPOCH, unpublished = false, postState = PostState.Pending, postAttempts = 0, difficulty = null,
    )

    private companion object {
        const val REPORTER = "uid-me"
    }
}
