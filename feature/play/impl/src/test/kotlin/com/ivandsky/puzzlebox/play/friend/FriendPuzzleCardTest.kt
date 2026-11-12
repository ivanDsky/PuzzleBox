package com.ivandsky.puzzlebox.play.friend

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.data.model.PostState
import com.ivandsky.puzzlebox.data.model.ReceivedPuzzle
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.designsystem.formatDuration
import com.ivandsky.puzzlebox.domain.LoadSharedPuzzle
import com.ivandsky.puzzlebox.domain.SyncReceivedStatus
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.Difficulty
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.PuzzleKey
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The friend puzzle card over its ViewModel, with every source a fake: the stored puzzle's
 * facts, the one primary action per state (Play fresh, Resume with the saved play's
 * elapsed, Play again once a solve is recorded), the difficulty chip only when the local
 * re-verify graded the board, and Copy putting the code on the clipboard, its glyph giving
 * way to a check for a moment.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class FriendPuzzleCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val game = TestGames.alpha
    private val fakes = FakeGames(listOf(game))
    private val code = requireNotNull(ShareCode.parse("K7Q2MX"))
    private val received = FakeReceivedPuzzleRepository()
    private val saved = FakeSavedPlayRepository()
    private val completions = FakeCompletionRepository()
    private val played = mutableListOf<CustomGameKey>()

    private fun puzzle(difficulty: Difficulty.Level? = Difficulty.Level.Hard) = ReceivedPuzzle(
        code = code,
        gameId = game,
        variant = VariantId("alpha/n8"),
        sharedFormatVersion = 1,
        puzzleBytes = byteArrayOf(1, 2, 3),
        title = "Crown the castle",
        authorName = "Jonas",
        note = "The corner is the trick",
        authorTimeMs = 102_000L,
        authorHints = 2,
        authorUid = "uid-jonas",
        openedAt = Instant.ofEpochMilli(1_000L),
        unpublished = false,
        postState = PostState.Pending,
        postAttempts = 0,
        difficulty = difficulty,
    )

    private fun show(puzzle: ReceivedPuzzle = puzzle()) {
        runBlocking { received.upsert(puzzle) }
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
            saved = saved,
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
                    FriendPuzzleCard(card = ready.card, onAction = { played += ready.card.play }, onSignIn = {})
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun node(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true)

    private fun count(tag: String) =
        composeRule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().size

    @Test
    fun `the card shows the puzzle's facts and its code`() {
        val puzzle = puzzle()
        show(puzzle)

        node("friend.card.glyph").assertIsDisplayed()
        node("friend.card.title").assert(hasText(puzzle.title))
        node("friend.card.author").assert(hasText(puzzle.authorName, substring = true))
        node("friend.card.note").assert(hasText(puzzle.note))
        node("friend.card.size").assert(hasText(puzzle.variant.value, substring = true))
        node("friend.card.difficulty").assert(hasText(Difficulty.Level.Hard.name, substring = true))
        node("friend.card.author.time").assert(hasText(formatDuration(puzzle.authorTimeMs), substring = true))
        node("friend.card.author.hints").assert(hasText(puzzle.authorHints.toString(), substring = true))
        node("friend.card.code").assert(hasText(code.display))
    }

    @Test
    fun `no difficulty chip when the board has no grade`() {
        show(puzzle(difficulty = null))

        node("friend.card.title").assertIsDisplayed()
        assertEquals(0, count("friend.card.difficulty"))
    }

    @Test
    fun `a fresh puzzle offers Play, which opens its board`() {
        show()

        node("friend.card.cta.play").assertExists()
        assertEquals(0, count("friend.card.cta.resume"))
        assertEquals(0, count("friend.card.solved"))
        assertEquals(0, count("friend.card.solved.hints"))
        node("friend.card.play").performClick()

        assertEquals(listOf(CustomGameKey(game.value, code.value)), played)
    }

    @Test
    fun `a saved play offers Resume with its elapsed time`() {
        runBlocking { saved.save(PlaySlot.Custom(game, code.value), byteArrayOf(9), elapsedMs = 42_000L) }
        show()

        node("friend.card.cta.resume").assertExists()
        composeRule.onNodeWithTag("friend.card.play").assert(hasText(formatDuration(42_000L), substring = true))
    }

    @Test
    fun `a recorded solve offers a play again and shows your time and hints`() {
        val ref = checkNotNull(fakes.games[game]).refOf(PuzzleKey.Custom(code.value))
        completions.setRecords(listOf(CompletionRecord(ref, 125_000L, 2, RecordStats(1, "{}"))))
        show()

        node("friend.card.cta.replay").assertExists()
        composeRule.onNodeWithTag("friend.card.play").assert(hasText("Play again"))
        node("friend.card.solved").assert(hasText(formatDuration(125_000L), substring = true))
        node("friend.card.solved.hints").assert(hasText("2", substring = true))
    }

    @Test
    fun `Copy puts the code on the clipboard`() {
        show()

        node("friend.card.copy").performClick()
        composeRule.waitForIdle()

        val clipboard = ApplicationProvider.getApplicationContext<Context>()
            .getSystemService(ClipboardManager::class.java)
        assertEquals(code.display, clipboard.primaryClip?.getItemAt(0)?.text?.toString())
    }

    @Test
    fun `Copy shows the copy glyph, a check for a moment after a tap, then the glyph again`() {
        show()
        node("friend.card.copy.icon").assertExists()

        composeRule.mainClock.autoAdvance = false
        node("friend.card.copy").performClick()
        composeRule.mainClock.advanceTimeUntil {
            count("friend.card.copied") == 1 && count("friend.card.copy.icon") == 0
        }

        composeRule.mainClock.advanceTimeUntil(timeoutMillis = COPIED_RETURN_MS) {
            count("friend.card.copy.icon") == 1 && count("friend.card.copied") == 0
        }
    }

    private companion object {
        const val COPIED_RETURN_MS = 3_000L
    }
}
