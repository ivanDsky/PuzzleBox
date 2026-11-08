package com.ivandsky.puzzlebox.app.e2e

import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
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
import com.ivandsky.puzzlebox.testing.flags.FakeReleaseFlags
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
 * reading Solved. Today's Tango stays unplayed and every daily read is as it was.
 */
@RunWith(RobolectricTestRunner::class)
// Plain Application: the merged manifest's PuzzleBoxApplication would start its own Koin.
// A real phone's height: the Studio's board slot needs room for its grid.
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
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
        releaseFlags = FakeReleaseFlags(ReleaseFlag.TangoEditor),
    )

    @Test
    fun `a published Tango draft opens by its code and its solve lands on its result, then the card reads solved`() =
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
            assertEquals("the shared doc is a Tango board", TangoDefinition.id.value, shared.docs.getValue(code).gameId)

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
                SOLUTION.forEachIndexed { idx, cell ->
                    if (idx !in GIVENS) controller.onPlacement(TangoPlacement(idx, cell))
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

    private fun ShellRobot.todaySet() = runBlocking { graph.koin.get<ObserveTodaySet>()().first() }

    private fun seedReadyDraft() {
        val editor = checkNotNull(graph.koin.get<GameRegistry>()[TangoDefinition.id]?.editor)
        val board = EditorDraft(TangoEditor, DraftState(TangoDraft(GIVENS.associateWith { SOLUTION[it] }, MARKERS)))
        runBlocking {
            drafts.upsert(
                Draft(
                    id = "sunmoon",
                    gameId = TangoDefinition.id,
                    variant = TangoEditor.variants.single(),
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
        const val TITLE = "Sun and moon"
        const val TIMEOUT_MS = 10_000L

        /** One unique 6×6 board's full solution, row-major. */
        val SOLUTION: List<TangoCell> = listOf("SSMSMM", "SSMSMM", "MMSMSS", "SSMMSM", "MMSSMS", "MMSMSS")
            .flatMap { row -> row.map { if (it == 'S') TangoCell.SUN else TangoCell.MOON } }

        /** The cells the draft gives; the friend places the rest. */
        val GIVENS = setOf(0, 1, 6, 7, 14, 16, 22, 29, 32, 33)

        val MARKERS = listOf(
            TangoMarker(3, TangoEdgeSide.DOWN, TangoMarkerKind.SAME),
            TangoMarker(18, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME),
            TangoMarker(18, TangoEdgeSide.DOWN, TangoMarkerKind.OPPOSITE),
            TangoMarker(34, TangoEdgeSide.RIGHT, TangoMarkerKind.SAME),
        )
    }
}
