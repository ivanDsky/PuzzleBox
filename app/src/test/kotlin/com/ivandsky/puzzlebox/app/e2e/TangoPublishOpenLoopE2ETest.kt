package com.ivandsky.puzzlebox.app.e2e

import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import com.ivandsky.puzzlebox.app.DrainTestApplication
import com.ivandsky.puzzlebox.create.api.CreateKey
import com.ivandsky.puzzlebox.data.identity.Identity
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.data.model.SharedPuzzleDoc
import com.ivandsky.puzzlebox.data.model.TestSolve
import com.ivandsky.puzzlebox.data.repository.DraftRepository
import com.ivandsky.puzzlebox.data.repository.PublishedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.ReceivedPuzzleRepository
import com.ivandsky.puzzlebox.data.repository.SharedPuzzleRepository
import com.ivandsky.puzzlebox.domain.ObserveTodaySet
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoDefinition
import com.ivandsky.puzzlebox.games.tango.TangoEdgeSide
import com.ivandsky.puzzlebox.games.tango.TangoMarker
import com.ivandsky.puzzlebox.games.tango.TangoMarkerKind
import com.ivandsky.puzzlebox.games.tango.editor.TangoDraft
import com.ivandsky.puzzlebox.games.tango.editor.TangoEditor
import com.ivandsky.puzzlebox.games.tangoui.TangoPlacement
import com.ivandsky.puzzlebox.games.tangoui.TangoPlayController
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
 * The sharing loop over the real shell for Tango: a signed-in author publishes a ready Tango
 * draft from the Studio and reads its code; a friend enters that code in Play, opens the card,
 * places the board's solution and lands on its result, whose Done returns to the card, now
 * reading Solved. Today's Tango stays unplayed and every daily read is as it was. A 6×6 doc
 * carries share version 1 (v1-only builds still open it); an 8×8 doc carries 2 (M4-tango-27).
 */
@RunWith(RobolectricTestRunner::class)
// Plain Application: the merged manifest's PuzzleBoxApplication would start its own Koin.
// A real phone's height: the Studio's board slot needs room for its grid.
@Config(sdk = [34], application = DrainTestApplication::class, qualifiers = "w411dp-h891dp")
class TangoPublishOpenLoopE2ETest {

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
    fun `a published Tango draft opens by its code and its solve lands on its result, then the card reads solved`() {
        val doc = publishOpenAndSolve(BOARD_6, SOLUTION_6)

        assertEquals("a 6×6 doc stays on share version 1", 1, doc.shareCodecVersion)
        assertEquals("tango/n6", doc.variant)
    }

    @Test
    fun `a published 8x8 Tango draft opens by its code and its solve lands on its result`() {
        val doc = publishOpenAndSolve(BOARD_8, SOLUTION_8)

        assertEquals("an 8×8 doc carries share version 2", 2, doc.shareCodecVersion)
        assertEquals("tango/n8", doc.variant)
    }

    /** Author publishes [board], a friend opens its code and places [solution]; returns the shared doc. */
    private fun publishOpenAndSolve(board: TangoDraft, solution: List<TangoCell>): SharedPuzzleDoc {
        var doc: SharedPuzzleDoc? = null
        shellRobot(graph = graph) {
            graph.identity.currentIdentity = Identity(uid = AUTHOR, displayName = "Ivan", photoUrl = null)
            shared.signedInUid = AUTHOR
            seedReadyDraft(board)
            val todayBefore = todaySet()

            tapTab(CreateKey)
            tap("create.draft.row")
            awaitTag("create.readiness.ready")
            tap("create.action.publish")
            awaitTag("create.published.code")
            val code = published.rows.keys.single()
            doc = shared.docs.getValue(code)
            assertEquals("the shared doc is the draft's", TITLE, doc!!.title)
            assertEquals("the shared doc is a Tango board", TangoDefinition.id.value, doc!!.gameId)

            tapTab(PlayKey)
            tap("library.enterCode")
            rule.onNodeWithTag("friend.code.field").performTextInput(code.display)
            tap("friend.code.open")
            awaitTag("friend.card.title")
            assertEquals("the friend received the published puzzle", TITLE, received.rows.getValue(code).title)
            tap("friend.card.play")
            awaitTag("route.game")

            val slot = PlaySlot.Custom(TangoDefinition.id, code.value)
            val store = graph.koin.get<PlaySessionStore>()
            rule.waitUntil(TIMEOUT_MS) { (store.peek(slot)?.controller as? TangoPlayController)?.session != null }
            val controller = store.peek(slot)?.controller as TangoPlayController
            rule.runOnIdle {
                solution.forEachIndexed { idx, cell ->
                    if (idx !in board.givens) controller.onPlacement(TangoPlacement(idx, cell))
                }
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
            assertTrue("today's Tango and every daily read are as they were", todayBefore == todaySet())
        }
        return checkNotNull(doc)
    }

    private fun ShellRobot.todaySet() = runBlocking { graph.koin.get<ObserveTodaySet>()().first() }

    private fun seedReadyDraft(board: TangoDraft) {
        val editor = checkNotNull(graph.koin.get<GameRegistry>()[TangoDefinition.id]?.editor)
        val draft = EditorDraft(TangoEditor, DraftState(board))
        runBlocking {
            drafts.upsert(
                Draft(
                    id = "sunmoon",
                    gameId = TangoDefinition.id,
                    variant = TangoEditor.variantOf(board),
                    draftFormatVersion = editor.draftFormatVersion,
                    blob = draft.encode(),
                    title = TITLE,
                    updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
                    testSolve = TestSolve(draft.layoutKey, timeMs = 102_000, hints = 0),
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
        const val TITLE = "Sun and moon"
        const val TIMEOUT_MS = 10_000L

        /** A full solution from row strings, row-major: `S` sun, `M` moon. */
        private fun cells(vararg rows: String): List<TangoCell> =
            rows.flatMap { row -> row.map { if (it == 'S') TangoCell.SUN else TangoCell.MOON } }

        /** One unique 6×6 board's full solution, row-major. */
        val SOLUTION_6: List<TangoCell> = cells("SSMSMM", "SSMSMM", "MMSMSS", "SSMMSM", "MMSSMS", "MMSMSS")

        /** Ten givens (the friend places the rest) and four markers. */
        val BOARD_6 = TangoDraft(
            6,
            setOf(0, 1, 6, 7, 14, 16, 22, 29, 32, 33).associateWith { SOLUTION_6[it] },
            listOf(
                TangoMarker(3, TangoEdgeSide.DOWN, TangoMarkerKind.SAME),
                TangoMarker(18, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME),
                TangoMarker(18, TangoEdgeSide.DOWN, TangoMarkerKind.OPPOSITE),
                TangoMarker(34, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME),
            ),
        )

        /** The full solution of `games/tango/src/test/resources/notation/tango-unique-8x8.txt`. */
        val SOLUTION_8: List<TangoCell> = cells(
            "MMSSMSSM", "MMSSMSSM", "SSMMSMMS", "MMSMSMSS",
            "SSMSMSMM", "SSMSMSMM", "MMSMSMSS", "SSMMSMMS",
        )

        /** `tango-unique-8x8.txt`: eleven givens and eleven markers, unique. */
        val BOARD_8 = TangoDraft(
            8,
            setOf(5, 11, 14, 16, 27, 32, 37, 43, 47, 58, 62).associateWith { SOLUTION_8[it] },
            listOf(
                TangoMarker(1, TangoEdgeSide.DOWN, TangoMarkerKind.SAME),
                TangoMarker(12, TangoEdgeSide.RIGHT, TangoMarkerKind.OPPOSITE),
                TangoMarker(18, TangoEdgeSide.DOWN, TangoMarkerKind.OPPOSITE),
                TangoMarker(33, TangoEdgeSide.DOWN, TangoMarkerKind.SAME),
                TangoMarker(37, TangoEdgeSide.DOWN, TangoMarkerKind.SAME),
                TangoMarker(38, TangoEdgeSide.DOWN, TangoMarkerKind.SAME),
                TangoMarker(41, TangoEdgeSide.RIGHT, TangoMarkerKind.OPPOSITE),
                TangoMarker(42, TangoEdgeSide.RIGHT, TangoMarkerKind.OPPOSITE),
                TangoMarker(51, TangoEdgeSide.RIGHT, TangoMarkerKind.OPPOSITE),
                TangoMarker(59, TangoEdgeSide.RIGHT, TangoMarkerKind.OPPOSITE),
                TangoMarker(62, TangoEdgeSide.RIGHT, TangoMarkerKind.OPPOSITE),
            ),
        )
    }
}
