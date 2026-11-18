package com.ivandsky.puzzlebox.play.friend

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.designsystem.HiddenBoardTokens
import com.ivandsky.puzzlebox.designsystem.SHIMMER_TAG
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.Spacing
import com.ivandsky.puzzlebox.domain.LoadSharedPuzzle
import com.ivandsky.puzzlebox.domain.ReportPuzzle
import com.ivandsky.puzzlebox.domain.SyncReceivedStatus
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.FakeGames
import com.ivandsky.puzzlebox.play.GOLDEN_TABLET
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
 * Ticket 132: the friend page in the Community tablet's right pane lays out two columns: the
 * board, chips and time tiles left; the head, title, note, Play and the ranks in a 372 dp column right. A pane too
 * narrow for both columns (320 dp board + 24 dp gap + 372 dp) and a full-screen page keep the single column.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = GOLDEN_TABLET)
class FriendPuzzleGridTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val game = TestGames.alpha
    private val fakes = FakeGames(listOf(game))
    private val code = requireNotNull(ShareCode.parse("K7Q2MX"))

    @Test
    fun `wide pane, the board is left of Play and of the ranks`() {
        show(showUp = false, pane = 880.dp)

        assertEquals(1, count("friend.card.grid"))
        val board = bounds("friend.card.board.hidden")
        val play = bounds("friend.card.play")
        val ranks = bounds("friend.card.board")
        assertTrue("board left of Play: $board vs $play", board.right <= play.left)
        assertTrue("board left of the ranks: $board vs $ranks", board.right <= ranks.left)
    }

    @Test
    fun `wide pane, chips and time tiles are in the left column and the head, title, note in the right`() {
        show(showUp = false, pane = 880.dp)

        val board = bounds("friend.card.board.hidden")
        val play = bounds("friend.card.play")
        listOf("friend.card.game", "friend.card.size", "friend.card.author.time").forEach { tag ->
            val part = bounds(tag)
            assertTrue("$tag left of Play", part.right <= play.left)
            assertTrue("$tag under the board", part.top >= board.bottom)
        }
        listOf("friend.card.glyph", "friend.card.title", "friend.card.note", "friend.card.code").forEach { tag ->
            assertTrue("$tag in Play's column", bounds(tag).left >= play.left - 1f)
        }
        assertTrue("the ranks under Play", bounds("friend.card.board").top >= play.bottom)
    }

    @Test
    fun `wide pane, the right column is 372 dp wide`() {
        show(showUp = false, pane = 880.dp)

        val right = bounds("friend.card.grid.right")
        assertEquals(with(composeRule.density) { 372.dp.toPx() }, right.width, 1f)
    }

    @Test
    fun `a tablet pane, the grid board is wider than today's cap and the board, chips and tiles fit the pane`() {
        show(showUp = false, pane = 1200.dp)

        val board = bounds("friend.card.board.hidden")
        val pane = bounds("route.friend")
        assertTrue("board ${board.width} wider than the cap", board.width > with(composeRule.density) { HiddenBoardTokens.MaxSide.toPx() })
        assertEquals("the board keeps its square", board.width, board.height, 1f)
        assertTrue("board starts in the pane", board.top >= pane.top)
        val tiles = bounds("friend.card.author.time")
        assertTrue("tiles end in the pane: ${tiles.bottom} of ${pane.bottom}", tiles.bottom <= pane.bottom)
    }

    @Test
    fun `a tablet pane, the board fills the left column's width when the height allows`() {
        show(showUp = false, pane = 880.dp)

        val board = bounds("friend.card.board.hidden")
        val right = bounds("friend.card.grid.right")
        val expected = with(composeRule.density) { (880.dp - Spacing.lg * 2 - Spacing.xl - 372.dp).toPx() }
        assertEquals(expected, board.width, 1f)
        assertTrue("left of the right column", board.right <= right.left)
    }

    @Test
    fun `a pane shorter than the board's bound keeps today's size and scrolls`() {
        show(showUp = false, pane = 1200.dp, height = 560.dp)

        val board = bounds("friend.card.board.hidden")
        assertEquals(with(composeRule.density) { HiddenBoardTokens.MaxSide.toPx() }, board.width, 1f)
    }

    @Test
    fun `a pane 880 dp wide, the columns sit vertically centred in the pane`() = assertCentred(880.dp)

    @Test
    fun `a pane 1000 dp wide, the columns sit vertically centred in the pane`() = assertCentred(1000.dp)

    @Test
    fun `a pane 1200 dp wide, the columns sit vertically centred in the pane`() = assertCentred(1200.dp)

    @Test
    fun `a tablet pane, the right column is centred against the left`() {
        show(showUp = false, pane = 880.dp)

        val left = bounds("friend.card.grid.left")
        val right = bounds("friend.card.grid.right")
        assertEquals("centres", (left.top + left.bottom) / 2f, (right.top + right.bottom) / 2f, 1f)
    }

    @Test
    fun `a tablet pane, loading draws the two-column skeleton and not the single bars or the spinner`() {
        openGate = CompletableDeferred()
        show(showUp = false, pane = 1200.dp)

        assertEquals(1, count("friend.card.grid.skeleton"))
        assertTrue("the skeleton bars sweep a sheen", count(SHIMMER_TAG) > 0)
        assertEquals(1, count("friend.card.grid.skeleton.right"))
        assertEquals(1, count("friend.card.loading.board"))
        assertEquals(0, count("friend.card.loading"))
        val board = bounds("friend.card.loading.board")
        assertTrue("board left of the right column", board.right <= bounds("friend.card.grid.skeleton.right").left)
    }

    @Test
    fun `a pane 880 dp wide, the skeleton's board is exactly where the loaded board lands`() = assertSkeletonInPlace(880.dp)

    @Test
    fun `a pane 1200 dp wide, the skeleton's board is exactly where the loaded board lands`() = assertSkeletonInPlace(1200.dp)

    @Test
    fun `a narrow pane keeps the single column's loading bars`() {
        openGate = CompletableDeferred()
        show(showUp = false, pane = 600.dp)

        assertEquals(0, count("friend.card.grid.skeleton"))
        assertEquals(1, count("friend.card.loading.board"))
    }

    @Test
    fun `a full-screen page keeps the single column's loading bars`() {
        openGate = CompletableDeferred()
        show(showUp = true, pane = 1280.dp)

        assertEquals(0, count("friend.card.grid.skeleton"))
        assertEquals(1, count("friend.card.loading.board"))
    }

    @Test
    fun `narrow pane falls back to the single column`() {
        show(showUp = false, pane = 600.dp)

        assertEquals(0, count("friend.card.grid"))
        val board = bounds("friend.card.board.hidden")
        assertTrue("board above Play", board.bottom <= bounds("friend.card.play").top)
        assertEquals(board.left, bounds("friend.card.play").left, board.width)
    }

    @Test
    fun `a pane just wide enough for both columns draws the grid`() {
        show(showUp = false, pane = 748.dp)
        assertEquals(1, count("friend.card.grid"))
    }

    @Test
    fun `a pane one dp short of both columns stays single`() {
        show(showUp = false, pane = 747.dp)
        assertEquals(0, count("friend.card.grid"))
    }

    @Test
    fun `a full-screen page keeps the single column`() {
        show(showUp = true, pane = 1280.dp)

        assertEquals(0, count("friend.card.grid"))
        assertTrue(bounds("friend.card.board.hidden").bottom <= bounds("friend.card.play").top)
    }

    private fun assertSkeletonInPlace(pane: Dp) {
        openGate = CompletableDeferred()
        show(showUp = false, pane = pane)
        val loading = bounds("friend.card.loading.board")
        openGate?.complete(Unit)
        composeRule.waitForIdle()

        assertEquals(loading, bounds("friend.card.board.hidden"))
    }

    /** The topmost and lowest parts of the page leave equal gaps above and below, within 1 dp. */
    private fun assertCentred(pane: Dp) {
        show(showUp = false, pane = pane)

        val route = bounds("route.friend")
        val top = minOf(bounds("friend.card.grid.left").top, bounds("friend.card.grid.right").top)
        val bottom = maxOf(bounds("friend.card.grid.left").bottom, bounds("friend.card.grid.right").bottom)
        val onePx = with(composeRule.density) { 1.dp.toPx() }
        assertEquals("gaps above and below", top - route.top, route.bottom - bottom, onePx)
    }

    private fun show(showUp: Boolean, pane: Dp, height: Dp = Dp.Unspecified) {
        fakes[game].replayBoard = StubGivensBoard()
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
            completions = FakeCompletionRepository(),
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
                Box(Modifier.width(pane).then(if (height.isSpecified) Modifier.height(height) else Modifier)) {
                    FriendPuzzleRoute(key = FriendPuzzleKey(code.value), onPlay = {}, onBack = {}, showUp = showUp, viewModel = viewModel)
                }
            }
        }
        composeRule.waitForIdle()
    }

    private var openGate: CompletableDeferred<Unit>? = null
    private val shared = FakeSharedPuzzleRepository().apply { signedInUid = "uid-me" }
    private val received = FakeReceivedPuzzleRepository()

    private fun node(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true)

    private fun count(tag: String) = composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().size

    private fun bounds(tag: String): Rect {
        val node = node(tag).fetchSemanticsNode()
        return Rect(node.positionInRoot, node.size.toSize())
    }

    private fun stored() = ReceivedPuzzle(
        code = code, gameId = game, variant = VariantId("alpha/n8"), sharedFormatVersion = 1,
        puzzleBytes = byteArrayOf(1, 2, 3), title = "Crown the castle", authorName = "Jonas",
        note = "The corner is the trick", authorTimeMs = 102_000L, authorHints = 0, authorUid = "uid-jonas",
        openedAt = Instant.EPOCH, unpublished = false, postState = PostState.Pending, postAttempts = 0, difficulty = null,
    )
}
