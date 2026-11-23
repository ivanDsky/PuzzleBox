package com.ivandsky.puzzlebox.app.e2e

import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import com.ivandsky.puzzlebox.app.DrainTestApplication
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
import com.ivandsky.puzzlebox.games.queens.QueensVariants
import com.ivandsky.puzzlebox.games.queens.editor.QueensDraft
import com.ivandsky.puzzlebox.games.queens.editor.QueensEditor
import com.ivandsky.puzzlebox.games.queensui.QueensPlayController
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PlaySlot
import com.ivandsky.puzzlebox.play.PlaySessionStore
import com.ivandsky.puzzlebox.play.api.CommunityKey
import com.ivandsky.puzzlebox.play.api.PlayKey
import com.ivandsky.puzzlebox.testing.repository.FakeDraftRepository
import com.ivandsky.puzzlebox.testing.repository.FakePublishedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeReceivedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeSharedPuzzleRepository
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The sharing loop over the real shell: a signed-in author publishes a ready Queens draft from
 * the Studio and reads its code; a friend enters that code in Play, opens the card, plays the
 * puzzle to the win and lands on its result, whose Done returns to the card, now reading Solved.
 * The author reaches the draft through Community › Yours and leaves the full-window Studio with Back.
 */
@RunWith(RobolectricTestRunner::class)
// DrainTestApplication (plain Application + looper drain): the merged manifest's PuzzleBoxApplication would start its own Koin.
// A real phone's height: the Studio's board slot needs room for its grid.
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w411dp-h891dp")
class PublishOpenLoopE2ETest {

    private val drafts = FakeDraftRepository()
    private val shared = FakeSharedPuzzleRepository()
    private val published = FakePublishedPuzzleRepository()
    private val received = FakeReceivedPuzzleRepository()

    private fun graph() = shellGraph(
        overrides = module {
            single<DraftRepository> { drafts }
            single<SharedPuzzleRepository> { shared }
            single<PublishedPuzzleRepository> { published }
            single<ReceivedPuzzleRepository> { received }
        },
    )

    @Test
    fun `a draft published from Community's Yours opens by its code and its solve lands on its result, then the card reads solved`() =
        publishOpenLoop(
            graph(),
            openDraft = {
                tapTab(CommunityKey)
                tap("shelves.entry.yours")
                tap("create.draft.row")
            },
            toGames = {
                // The Studio is full-window: Back is the way out, to Yours, which now lists the puzzle.
                back()
                assertRoute("route.community")
                awaitTag("create.published.row")
                tapTab(PlayKey)
            },
        )

    private fun publishOpenLoop(
        graph: E2eKoinGraph,
        openDraft: ShellRobot.() -> Unit,
        toGames: ShellRobot.() -> Unit,
    ) = shellRobot(graph = graph) {
        graph.identity.currentIdentity = Identity(uid = AUTHOR, displayName = "Ivan", photoUrl = null)
        shared.signedInUid = AUTHOR
        seedReadyDraft(graph)

        openDraft()
        awaitTag("create.readiness.ready")
        tap("create.action.publish")
        awaitTag("create.published.code")
        val code = published.rows.keys.single()
        assertEquals("the shared doc is the draft's", TITLE, shared.docs.getValue(code).title)

        toGames()
        tap("library.enterCode")
        rule.onNodeWithTag("friend.code.field").performTextInput(code.display)
        tap("friend.code.open")
        awaitTag("friend.card.title")
        assertEquals("the friend received the published puzzle", TITLE, received.rows.getValue(code).title)
        tap("friend.card.play")
        awaitTag("route.game")

        val slot = PlaySlot.Custom(GameId(QUEENS), code.value)
        val store = graph.koin.get<PlaySessionStore>()
        rule.waitUntil(TIMEOUT_MS) { (store.peek(slot)?.controller as? QueensPlayController)?.board != null }
        val board = checkNotNull((store.peek(slot)?.controller as QueensPlayController).board)
        // Two taps place a queen; the board's one solution, row by row.
        rule.runOnIdle { SOLUTION.forEach { cell -> repeat(2) { board.onTap(cell) } } }

        rule.waitUntil(TIMEOUT_MS) {
            // A bare waitUntil drives no frame here; pump one so the win's handoff runs.
            rule.runOnIdle { }
            rule.onAllNodesWithTag("route.custom_result", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }
        assertRoute("route.custom_result")
        awaitTag("result.hero.pill")

        tap("result.done")
        awaitTag("friend.card.solved")
        assertRoute("route.friend")
    }

    private fun seedReadyDraft(graph: E2eKoinGraph) {
        val editor = checkNotNull(graph.koin.get<GameRegistry>()[GameId(QUEENS)]?.editor)
        val board = EditorDraft(QueensEditor, DraftState(QueensDraft(SIZE, regions(UNIQUE))))
        runBlocking {
            drafts.upsert(
                Draft(
                    id = "castle",
                    gameId = GameId(QUEENS),
                    variant = QueensVariants.variant(SIZE).id,
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

    /** Row-major region ids from letter rows ('A' = 0), space-separated. */
    private fun regions(rows: String): List<Int> = rows.filter { it != ' ' }.map { it - 'A' }

    private companion object {
        const val AUTHOR = "author"
        const val QUEENS = "queens"
        const val TITLE = "Castle"
        const val SIZE = 6

        /** One solution on a 6×6 board. */
        const val UNIQUE = "BBBBAA BBBBCA EBBDCA EEDDDD EEFFDF EEFFFF"

        /** UNIQUE's solution as row-major cell indices. */
        val SOLUTION = listOf(5, 7, 16, 20, 24, 33)
        const val TIMEOUT_MS = 10_000L
    }
}
