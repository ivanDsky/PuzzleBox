package com.ivandsky.puzzlebox.play.friend

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.LoadSharedPuzzle
import com.ivandsky.puzzlebox.domain.SyncReceivedStatus
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.FakeGames
import com.ivandsky.puzzlebox.play.api.CustomGameKey
import com.ivandsky.puzzlebox.play.api.FriendPuzzleKey
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSavedPlayRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A friend's puzzle whose author took it down, opened again from this device: online, the card
 * rechecks the doc, marks the puzzle and says it is taken down but still plays, its board closed,
 * and Play still opens it; offline the card keeps the puzzle's last known state.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class TakenDownCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val game = TestGames.alpha
    private val fakes = FakeGames(listOf(game))
    private val code = requireNotNull(ShareCode.parse("K7Q2MX"))
    private val shared = FakeSharedPuzzleRepository().apply { signedInUid = AUTHOR }
    private val received = FakeReceivedPuzzleRepository()
    private val played = mutableListOf<CustomGameKey>()

    private fun stored(unpublished: Boolean = false) = ReceivedPuzzle(
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
        authorUid = AUTHOR,
        openedAt = Instant.EPOCH,
        unpublished = unpublished,
        postState = PostState.Pending,
        postAttempts = 0,
        difficulty = null,
    )

    /** The author's doc, as published, then taken down when [takenDown]. */
    private fun givenDoc(takenDown: Boolean) = runBlocking {
        shared.publish(
            SharedPuzzleDoc(
                code = code, formatVersion = SharedPuzzleDoc.FORMAT_VERSION, gameId = game.value, variant = "alpha/n8",
                shareCodecVersion = 1, payload = byteArrayOf(1, 2, 3), title = "Crown the castle", authorName = "Jonas Lind",
                note = "", authorUid = AUTHOR, authorTimeMs = 102_000L, authorHints = 0, createdAtMs = 0L, unpublished = false,
            ),
        )
        if (takenDown) shared.unpublish(code)
    }

    private fun show(row: ReceivedPuzzle = stored()) {
        runBlocking { received.upsert(row) }
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
        )
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                val state by viewModel.state.collectAsState()
                (state as? FriendCardState.Ready)?.let { ready ->
                    FriendPuzzleCard(
                        card = ready.card,
                        onAction = { played += ready.card.play },
                        onSignIn = {},
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                    )
                }
            }
        }
        composeRule.waitUntil(WAIT_MS) { count("friend.card.title") > 0 }
        composeRule.waitForIdle()
    }

    @Test
    fun `online, a taken-down puzzle is marked and its card says so, with the board closed`() {
        givenDoc(takenDown = true)
        show()
        composeRule.waitUntil(WAIT_MS) { count("friend.card.taken_down") > 0 }

        assertTrue(checkNotNull(received.rows[code]).unpublished)
        node("custom_board.closed").assertExists()
        assertEquals("a closed board offers no sign-in", 0, count("custom_board.sign_in"))
    }

    @Test
    fun `a taken-down puzzle still plays`() {
        givenDoc(takenDown = true)
        show()
        composeRule.waitUntil(WAIT_MS) { count("friend.card.taken_down") > 0 }

        node("friend.card.cta.play").assertExists()
        node("friend.card.play").performClick()

        assertEquals(listOf(CustomGameKey(game.value, code.value)), played)
    }

    @Test
    fun `a live puzzle stays open`() {
        givenDoc(takenDown = false)
        show()

        assertFalse(checkNotNull(received.rows[code]).unpublished)
        assertEquals(0, count("friend.card.taken_down"))
        assertEquals(0, count("custom_board.closed"))
        node("custom_board.sign_in").assertExists()
    }

    @Test
    fun `offline, the card keeps a live puzzle open`() {
        givenDoc(takenDown = true)
        shared.online = false
        show()

        assertFalse(checkNotNull(received.rows[code]).unpublished)
        assertEquals(0, count("friend.card.taken_down"))
        assertEquals(0, count("custom_board.closed"))
    }

    @Test
    fun `offline, the card keeps a puzzle already marked taken down`() {
        shared.online = false
        show(stored(unpublished = true))

        node("friend.card.taken_down").assertExists()
        node("custom_board.closed").assertExists()
    }

    private fun node(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true)

    private fun count(tag: String) =
        composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().size

    private companion object {
        const val AUTHOR = "uid-jonas"
        const val WAIT_MS = 5_000L
    }
}
