package com.ivandsky.puzzlebox.play.friend

import androidx.compose.ui.test.assertIsDisplayed
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
import com.ivandsky.puzzlebox.play.domain.ReportPuzzle
import com.ivandsky.puzzlebox.play.domain.SyncReceivedStatus
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.model.ShareCode
import com.ivandsky.puzzlebox.play.FakeGames
import com.ivandsky.puzzlebox.play.api.FriendPuzzleKey
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
 * Report on the friend route, over the real use case and fake backend: the ⋮ offers Report; Report
 * in its menu closes the card behind a "Reported" snackbar and then leaves; signed out it opens the sign-in prompt and
 * the card stays.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class FriendReportRouteTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val game = TestGames.alpha
    private val fakes = FakeGames(listOf(game))
    private val code = requireNotNull(ShareCode.parse("K7Q2MX"))
    private val shared = FakeSharedPuzzleRepository().apply { signedInUid = AUTHOR }
    private val received = FakeReceivedPuzzleRepository()
    private val hidden = FakeHiddenPuzzles()
    private var backs = 0

    @Test
    fun `the card has an overflow that offers Report`() {
        show()

        node("friend.card.more").assertIsDisplayed().performClick()
        node("friend.card.report").assertIsDisplayed()
    }
    @Test
    fun `Report hides the puzzle, says so, then leaves the card`() {
        show()

        node("friend.card.more").performClick()
        node("friend.card.report").performClick()
        composeRule.waitForIdle()

        node("friend.notice.reported").assertIsDisplayed()
        assertEquals(setOf(code), hidden.hidden)
        assertEquals(0, backs)

        composeRule.mainClock.advanceTimeBy(REPORTED_NOTICE_MS + 500)

        assertEquals(1, backs)
    }

    @Test
    fun `signed out, Report opens the sign-in prompt and the card stays`() {
        show(signedIn = false)

        node("friend.card.more").performClick()
        node("friend.card.report").performClick()
        composeRule.waitForIdle()

        node("community.signin").assertIsDisplayed()
        node("friend.card.title").assertIsDisplayed()
        assertTrue(hidden.hidden.isEmpty())
        assertEquals(0, backs)

        node("community.signin.dismiss").performClick()
        composeRule.waitForIdle()

        assertEquals(0, composeRule.onAllNodes(hasTestTag("community.signin")).fetchSemanticsNodes().size)
    }

    private fun show(signedIn: Boolean = true) {
        runBlocking {
            shared.publish(doc())
            received.upsert(stored())
        }
        shared.signedInUid = if (signedIn) REPORTER else null
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
            reportPuzzle = ReportPuzzle(shared, hidden),
        )
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                FriendPuzzleRoute(key = FriendPuzzleKey(code.value), onPlay = {}, onBack = { backs++ }, viewModel = viewModel)
            }
        }
        composeRule.waitForIdle()
    }

    private fun node(tag: String) = composeRule.onNodeWithTag(tag, useUnmergedTree = true)

    private fun doc() = SharedPuzzleDoc(
        code = code, formatVersion = SharedPuzzleDoc.FORMAT_VERSION, gameId = game.value, variant = "alpha/n8",
        shareCodecVersion = 1, payload = byteArrayOf(1, 2, 3), title = "Crown the castle", authorName = "Jonas",
        note = "", authorUid = AUTHOR, authorTimeMs = 102_000L, authorHints = 0, createdAtMs = 0L, unpublished = false,
    )

    private fun stored() = ReceivedPuzzle(
        code = code, gameId = game, variant = VariantId("alpha/n8"), sharedFormatVersion = 1,
        puzzleBytes = byteArrayOf(1, 2, 3), title = "Crown the castle", authorName = "Jonas", note = "",
        authorTimeMs = 102_000L, authorHints = 0, authorUid = AUTHOR, openedAt = Instant.EPOCH,
        unpublished = false, postState = PostState.Pending, postAttempts = 0, difficulty = null,
    )

    private companion object {
        const val AUTHOR = "uid-jonas"
        const val REPORTER = "uid-me"
    }
}
