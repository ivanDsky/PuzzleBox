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
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.Wall
import com.ivandsky.puzzlebox.games.zip.WallDirection
import com.ivandsky.puzzlebox.games.zip.ZipDefinition
import com.ivandsky.puzzlebox.games.zip.editor.ZipDraft
import com.ivandsky.puzzlebox.games.zip.editor.ZipEditor
import com.ivandsky.puzzlebox.games.zipui.ZipPlayController
import com.ivandsky.puzzlebox.gameui.DragEvent
import com.ivandsky.puzzlebox.gameui.GridCell
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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The sharing loop over the real shell for Zip: a signed-in author publishes a ready Zip
 * draft from the Studio and reads its code; a friend enters that code in Play, opens the card,
 * traces the board's one path and lands on its result, whose Done returns to the card, now
 * reading Solved. Today's Zip stays unplayed and every daily read is as it was.
 */
@RunWith(RobolectricTestRunner::class)
// Plain Application: the merged manifest's PuzzleBoxApplication would start its own Koin.
// A real phone's height: the Studio's board slot needs room for its grid.
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w411dp-h891dp")
class ZipPublishOpenLoopE2ETest {

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
    fun `a published Zip draft opens by its code and its solve lands on its result, then the card reads solved`() =
        shellRobot(graph = graph) {
            graph.identity.currentIdentity = Identity(uid = AUTHOR, displayName = "Ivan", photoUrl = null)
            shared.signedInUid = AUTHOR
            seedReadyDraft()
            val todayBefore = todaySet()

            tapTab(CreateKey)
            tap("create.draft.row")
            awaitTag("create.readiness.ready")
            tap("create.action.publish")
            awaitTag("create.published.code")
            val code = published.rows.keys.single()
            assertEquals("the shared doc is the draft's", TITLE, shared.docs.getValue(code).title)
            assertEquals("the shared doc is a Zip board", ZipDefinition.id.value, shared.docs.getValue(code).gameId)

            tapTab(PlayKey)
            tap("library.enterCode")
            rule.onNodeWithTag("friend.code.field").performTextInput(code.display)
            tap("friend.code.open")
            awaitTag("friend.card.title")
            assertEquals("the friend received the published puzzle", TITLE, received.rows.getValue(code).title)
            tap("friend.card.play")
            awaitTag("route.game")

            val slot = PlaySlot.Custom(ZipDefinition.id, code.value)
            val store = graph.koin.get<PlaySessionStore>()
            rule.waitUntil(TIMEOUT_MS) { (store.peek(slot)?.controller as? ZipPlayController)?.session != null }
            val controller = store.peek(slot)?.controller as ZipPlayController
            rule.runOnIdle {
                SOLUTION.forEach { controller.onDragEvent(DragEvent.Extend(GridCell(it / SIZE, it % SIZE))) }
            }

            rule.waitUntil(TIMEOUT_MS) {
                // A bare waitUntil drives no frame here; pump one so the win's handoff runs.
                rule.runOnIdle { }
                rule.onAllNodesWithTag("route.custom_result", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
            assertRoute("route.custom_result")
            awaitTag("result.record_badge")
            val records = runBlocking { graph.records.observeRecords().first() }
            assertEquals("the win is the one record, on the friend's board", code.value, (records.single().puzzleRef.key as PuzzleKey.Custom).puzzleId)

            tap("result.done")
            awaitTag("friend.card.solved")
            assertRoute("route.friend")
            assertTrue("today's Zip and every daily read are as they were", todayBefore == todaySet())
        }

    private fun ShellRobot.todaySet() = runBlocking { graph.koin.get<ObserveTodaySet>()().first() }

    private fun seedReadyDraft() {
        val editor = checkNotNull(graph.koin.get<GameRegistry>()[ZipDefinition.id]?.editor)
        val board = EditorDraft(ZipEditor, DraftState(DRAFT))
        runBlocking {
            drafts.upsert(
                Draft(
                    id = "corridor",
                    gameId = ZipDefinition.id,
                    variant = ZipEditor.variantOf(DRAFT),
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
        const val TITLE = "Corridor"
        const val TIMEOUT_MS = 10_000L

        const val SIZE = 5

        /** The forced 5×5 corridor board's one path, row-major cell indices. */
        val SOLUTION = listOf(0, 1, 2, 3, 4, 9, 8, 7, 6, 5, 10, 11, 12, 13, 14, 19, 18, 17, 16, 15, 20, 21, 22, 23, 24)

        /** Walls under these cells force [SOLUTION]; the waypoints number its ends and middle. */
        private val WALLED = listOf(0, 1, 2, 3, 6, 7, 8, 9, 10, 11, 12, 13, 16, 17, 18, 19)

        val DRAFT = ZipDraft(
            n = SIZE,
            walls = WALLED.map { Wall(Cell(it), WallDirection.DOWN) }.toSet(),
            path = SOLUTION,
            waypoints = listOf(0, 12, 24),
        )
    }
}
