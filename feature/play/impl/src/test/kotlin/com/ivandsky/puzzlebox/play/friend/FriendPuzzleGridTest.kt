package com.ivandsky.puzzlebox.play.friend

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
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
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeHiddenPuzzles
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import java.time.Instant
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
 * Ticket 132: the friend page in the Community tablet's right pane lays out two columns with CommunityPuzzles on: the
 * board, chips and time tiles left; the head, title, note, Play and the ranks in a 372 dp column right. A pane too
 * narrow for both columns (320 dp board + 24 dp gap + 372 dp), a full-screen page and the flag off keep the single column.
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
    fun `wide pane, flag on, the board is left of Play and of the ranks`() {
        show(communityOn = true, showUp = false, pane = 880.dp)

        assertEquals(1, count("friend.card.grid"))
        val board = bounds("friend.card.board.hidden")
        val play = bounds("friend.card.play")
        val ranks = bounds("friend.card.board")
        assertTrue("board left of Play: $board vs $play", board.right <= play.left)
        assertTrue("board left of the ranks: $board vs $ranks", board.right <= ranks.left)
    }

    @Test
    fun `wide pane, flag on, chips and time tiles are in the left column and the head, title, note in the right`() {
        show(communityOn = true, showUp = false, pane = 880.dp)

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
    fun `wide pane, flag on, the right column is 372 dp wide`() {
        show(communityOn = true, showUp = false, pane = 880.dp)

        val right = bounds("friend.card.grid.right")
        assertEquals(with(composeRule.density) { 372.dp.toPx() }, right.width, 1f)
    }

    @Test
    fun `wide pane, flag on, both columns start at the top`() {
        show(communityOn = true, showUp = false, pane = 880.dp)

        assertEquals(bounds("friend.card.board.hidden").top, bounds("friend.card.grid.right").top, 1f)
    }

    @Test
    fun `narrow pane, flag on, falls back to the single column`() {
        show(communityOn = true, showUp = false, pane = 600.dp)

        assertEquals(0, count("friend.card.grid"))
        val board = bounds("friend.card.board.hidden")
        assertTrue("board above Play", board.bottom <= bounds("friend.card.play").top)
        assertEquals(board.left, bounds("friend.card.play").left, board.width)
    }

    @Test
    fun `a pane just wide enough for both columns draws the grid`() {
        show(communityOn = true, showUp = false, pane = 748.dp)
        assertEquals(1, count("friend.card.grid"))
    }

    @Test
    fun `a pane one dp short of both columns stays single`() {
        show(communityOn = true, showUp = false, pane = 747.dp)
        assertEquals(0, count("friend.card.grid"))
    }

    @Test
    fun `wide pane, flag off, is the shipped single column with no board`() {
        show(communityOn = false, showUp = false, pane = 880.dp)

        assertEquals(0, count("friend.card.grid"))
        assertEquals(0, count("friend.card.board.hidden"))
        assertTrue(bounds("friend.card.size").bottom <= bounds("friend.card.play").top)
    }

    @Test
    fun `a full-screen page with the flag on keeps the single column`() {
        show(communityOn = true, showUp = true, pane = 1280.dp)

        assertEquals(0, count("friend.card.grid"))
        assertTrue(bounds("friend.card.board.hidden").bottom <= bounds("friend.card.play").top)
    }

    private fun show(communityOn: Boolean, showUp: Boolean, pane: Dp) {
        fakes[game].replayBoard = StubGivensBoard()
        runBlocking { received.upsert(stored()) }
        val now = object : DispatcherProvider {
            override val main = Dispatchers.Unconfined
            override val io = Dispatchers.Unconfined
            override val default = Dispatchers.Unconfined
        }
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
            completions = FakeCompletionRepository(),
            saved = FakeSavedPlayRepository(),
            games = fakes.games,
            plays = fakes.registry,
            editors = GameEditorRegistry(emptyList()),
            dispatchers = now,
            board = signedOutBoard(),
            flags = if (communityOn) FakeReleaseFlags(ReleaseFlag.CommunityPuzzles) else FakeReleaseFlags(),
            reportPuzzle = ReportPuzzle(shared, FakeHiddenPuzzles()),
        )
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Box(Modifier.width(pane)) {
                    FriendPuzzleRoute(key = FriendPuzzleKey(code.value), onPlay = {}, onBack = {}, showUp = showUp, viewModel = viewModel)
                }
            }
        }
        composeRule.waitForIdle()
    }

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
