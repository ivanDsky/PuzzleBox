package com.ivandsky.puzzlebox.app.e2e

import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.create.api.CreateKey
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.data.model.TestSolve
import com.ivandsky.puzzlebox.data.repository.DraftRepository
import com.ivandsky.puzzlebox.data.repository.PublishedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.SharedPuzzleRepository
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.editor.SudokuDraft
import com.ivandsky.puzzlebox.games.sudoku.editor.SudokuEditor
import com.ivandsky.puzzlebox.games.sudokuui.SudokuPlayController
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.play.PlaySessionStore
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.testing.repository.FakeDraftRepository
import com.ivandsky.puzzlebox.testing.repository.FakePublishedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The sharing loop over the real shell for Mini Sudoku: a signed-in author publishes a ready
 * draft from the Studio and reads its code; a friend enters it in Play, opens the card, fills
 * the board to the win and lands on its result. The win is a custom record, never a daily one.
 */
@RunWith(RobolectricTestRunner::class)
// DrainTestApplication (plain Application + looper drain): the merged manifest's PuzzleBoxApplication would start its own Koin.
// A real phone's height: the Studio's board slot needs room for its grid.
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w411dp-h891dp")
class SudokuPublishOpenLoopE2ETest {

    private val drafts = FakeDraftRepository()
    private val shared = FakeSharedPuzzleRepository()
    private val published = FakePublishedPuzzleRepository()
    private val received = FakeReceivedPuzzleRepository()

    private val graph = E2eKoinGraph(
        E2E_TODAY,
        overrides = module {
            single<DraftRepository> { drafts }
            single<SharedPuzzleRepository> { shared }
            single<PublishedPuzzleRepository> { published }
            single<ReceivedPuzzleRepository> { received }
        },
    )

    @Test
    fun `a published Mini Sudoku draft opens by its code and its solve lands on its result`() = shellRobot(graph = graph) {
        graph.identity.currentIdentity = Identity(uid = AUTHOR, displayName = "Ivan", photoUrl = null)
        shared.signedInUid = AUTHOR
        seedReadyDraft()

        tapTab(CreateKey)
        tap("create.draft.row")
        awaitTag("create.readiness.ready")
        tap("create.action.publish")
        awaitTag("create.published.code")
        val code = published.rows.keys.single()
        assertEquals("the shared doc is the draft's", TITLE, shared.docs.getValue(code).title)

        tapTab(PlayKey)
        tap("library.enterCode")
        rule.onNodeWithTag("friend.code.field").performTextInput(code.display)
        tap("friend.code.open")
        awaitTag("friend.card.title")
        assertEquals("the friend received the published puzzle", TITLE, received.rows.getValue(code).title)
        tap("friend.card.play")
        awaitTag("route.game")

        val slot = PlaySlot.Custom(MINI.id, code.value)
        val store = graph.koin.get<PlaySessionStore>()
        rule.waitUntil(TIMEOUT_MS) { (store.peek(slot)?.controller as? SudokuPlayController)?.board != null }
        val board = checkNotNull((store.peek(slot)?.controller as SudokuPlayController).board)
        // One entry per frame: the motion reads each commit against the last, and only the
        // single last entry of a solve is the win it hands off on.
        GIVENS.forEachIndexed { cell, given ->
            if (given == 0) {
                rule.runOnIdle {
                    board.select(cell)
                    board.input(SOLUTION[cell])
                }
                rule.waitForIdle()
            }
        }

        rule.waitUntil(TIMEOUT_MS) {
            // A bare waitUntil drives no frame here; pump one so the win's handoff runs.
            rule.runOnIdle { }
            rule.onAllNodesWithTag("route.custom_result", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertRoute("route.custom_result")
        awaitTag("result.record_badge")

        val keys = runBlocking { graph.records.observeRecords().first() }.map { it.puzzleRef.key }
        assertEquals("the win is one custom record", 1, keys.size)
        assertFalse("no daily record", keys.any { it is PuzzleKey.Daily })

        tap("result.done")
        awaitTag("friend.card.solved")
        assertRoute("route.friend")
    }

    private fun seedReadyDraft() {
        val editor = checkNotNull(graph.koin.get<GameRegistry>()[MINI.id]?.editor)
        val board = EditorDraft(SudokuEditor(MINI), DraftState(SudokuDraft(MINI.variant.geometry.size, GIVENS)))
        runBlocking {
            drafts.upsert(
                Draft(
                    id = "mini",
                    gameId = MINI.id,
                    variant = MINI.variant.id,
                    draftFormatVersion = editor.draftFormatVersion,
                    blob = board.encode(),
                    title = TITLE,
                    updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
                    testSolve = TestSolve(board.layoutKey, timeMs = 102_000, hints = 0),
                    reservedCode = null,
                    imported = false,
                    authorName = "",
                    note = "",
                ),
            )
        }
    }

    private companion object {
        const val AUTHOR = "author"
        const val TITLE = "Corner"
        const val TIMEOUT_MS = 10_000L
        val MINI = SudokuGames.MINI

        /** A 6×6 board with one solution the hint ladder finishes; `0` is an empty cell. */
        val GIVENS = grid("....62..643..2.5.3..1....3...4.65.2.")
        val SOLUTION = grid("543162216435624513351246132654465321")

        fun grid(text: String): List<Int> = text.map { if (it == '.') 0 else it.digitToInt() }
    }
}
