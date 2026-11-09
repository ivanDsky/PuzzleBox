package com.ivandsky.puzzlebox.app.e2e

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import com.ivandsky.puzzlebox.common.flags.ReleaseFlag
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
import com.ivandsky.puzzlebox.game.editor.PublishBlock
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strands.StrandsDefinition
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraft
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraftPath
import com.ivandsky.puzzlebox.games.strands.editor.StrandsEditor
import com.ivandsky.puzzlebox.games.strands.editor.StrandsPublishGates
import com.ivandsky.puzzlebox.games.strandsui.StrandsPlayController
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The sharing loop over the real shell for Strands: a signed-in author publishes a ready draft
 * from the Studio and reads its code; a friend enters it in Play, opens the card, finds three
 * non-theme words the recipient's re-derived dictionary accepts (a hint is earned), finds every
 * theme word and lands on the result. The win is a custom record, never a daily one.
 */
@RunWith(RobolectricTestRunner::class)
// Plain Application: the merged manifest's PuzzleBoxApplication would start its own Koin.
// A phone-sized window: the default Robolectric screen would push the 8-row board off it.
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h891dp")
class StrandsPublishOpenLoopE2ETest {

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
        releaseFlags = FakeReleaseFlags(ReleaseFlag.StrandsEditor),
    )

    @Test
    fun `a published Strands draft opens by its code, earns a hint and its solve lands on its result`() =
        shellRobot(graph = graph) {
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

            val slot = PlaySlot.Custom(StrandsDefinition.id, code.value)
            val store = graph.koin.get<PlaySessionStore>()
            fun controller() = checkNotNull(store.peek(slot)).controller as StrandsPlayController
            rule.waitUntil(TIMEOUT_MS) {
                rule.runOnIdle { (store.peek(slot)?.controller as? StrandsPlayController)?.session } != null
            }
            awaitTag(BOARD)

            NON_THEME.forEach { tapWord(it) }
            assertEquals("each tapped word counts", NON_THEME.size, rule.runOnIdle { controller().session?.state?.nonTheme?.size })
            assertTrue("three non-theme words earn a hint", rule.runOnIdle { controller().hint.available })

            (THEME + listOf(SPANGRAM)).forEach { drag(it) }

            rule.waitUntil(TIMEOUT_MS) {
                rule.runOnIdle { }
                rule.onAllNodesWithTag("route.custom_result", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
            assertRoute("route.custom_result")
            awaitTag("result.record_badge")

            val keys = runBlocking { graph.records.observeRecords().first() }.map { it.puzzleRef.key }
            assertEquals("the win is one custom record", 1, keys.size)
            assertFalse("no daily record", keys.any { it is PuzzleKey.Daily })
            val daily = PlaySlot.Daily(StrandsDefinition.id, E2E_TODAY)
            assertNull("no daily Strands session was opened", rule.runOnIdle { store.peek(daily) })

            tap("result.done")
            awaitTag("friend.card.solved")
            assertRoute("route.friend")
        }

    @Test
    fun `an unclued Strands draft reads its clue as a field to fill, not a board to fix`() =
        shellRobot(graph = graph) {
            seedReadyDraft(clue = "")

            tapTab(CreateKey)
            tap("create.draft.row")
            fun count(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().size
            // Unique: the Not unique block is gone and the clue's own phrase stands in the line.
            rule.waitUntil(TIMEOUT_MS) {
                count("create.readiness.$CLUE_BLOCK") == 1 && count("create.readiness.${PublishBlock.NOT_UNIQUE}") == 0
            }
            assertEquals("a missing clue is not the board's to fix", 0, count("create.readiness.board"))
            assertEquals("the clue still blocks", 0, count("create.readiness.ready"))
        }

    @Test
    fun `Undo and Redo stay reachable with the details and revert a clue edit`() =
        shellRobot(graph = graph) {
            seedReadyDraft()
            fun clue() = rule.onNodeWithTag("strands.editor.clue").fetchSemanticsNode().config[SemanticsProperties.EditableText].text

            tapTab(CreateKey)
            tap("create.draft.row")
            awaitTag("create.details")
            rule.waitUntil(TIMEOUT_MS) { rule.onAllNodesWithTag("create.tool.strip").fetchSemanticsNodes().isEmpty() }
            rule.onNodeWithTag("create.tool.undo").assertIsNotEnabled()

            rule.onNodeWithTag("strands.editor.clue").performTextReplacement("Autumn walk in the woods at dusk")
            rule.waitForIdle()
            assertEquals("Autumn walk in the woods at dusk", clue())
            rule.onNodeWithTag("create.tool.undo").assertIsEnabled()

            tap("create.tool.undo")
            assertEquals("Autumn walk in the woods", clue())
            rule.onNodeWithTag("create.tool.undo").assertIsNotEnabled()
            rule.onNodeWithTag("create.tool.redo").assertIsEnabled()

            tap("create.tool.redo")
            assertEquals("Autumn walk in the woods at dusk", clue())
        }

    private fun seedReadyDraft(clue: String = "Autumn walk in the woods") {
        val editor = checkNotNull(graph.koin.get<GameRegistry>()[StrandsDefinition.id]?.editor)
        val board = EditorDraft(StrandsEditor, DraftState(draft(clue)))
        runBlocking {
            drafts.upsert(
                Draft(
                    id = "strands",
                    gameId = StrandsDefinition.id,
                    variant = StrandsDefinition.VARIANT,
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

    private fun draft(clue: String): StrandsDraft {
        val words = THEME.zip(THEME_WORDS) + (SPANGRAM to SPANGRAM_WORD)
        return StrandsDraft(
            letters = LETTERS.joinToString(""),
            clue = clue,
            paths = words.map { (cells, word) -> StrandsDraftPath(cells.map { it.row * COLS + it.col }, word) },
            spangram = words.lastIndex,
        )
    }

    private fun ShellRobot.drag(path: List<Coord>) {
        rule.onNodeWithTag(BOARD).performScrollTo().performTouchInput {
            down(centreOf(path.first()))
            path.drop(1).forEach { moveTo(centreOf(it)) }
            up()
        }
        rule.waitForIdle()
    }

    /** Taps each letter, then the last one again: the double-tap submit. */
    private fun ShellRobot.tapWord(path: List<Coord>) {
        (path + path.last()).forEach { at ->
            rule.onNodeWithTag("strands.cell.${at.row}.${at.col}").performScrollTo().performClick()
        }
        rule.waitForIdle()
    }

    private fun TouchInjectionScope.centreOf(at: Coord) =
        Offset(width * (at.col + 0.5f) / COLS, height * (at.row + 0.5f) / ROWS)

    private companion object {
        const val AUTHOR = "author"
        const val TITLE = "Woods"
        const val TIMEOUT_MS = 10_000L
        const val CLUE_BLOCK = StrandsPublishGates.CLUE_LENGTH
        const val BOARD = "strands.board"
        const val ROWS = 8
        const val COLS = 6

        val LETTERS = listOf("FNGHCN", "SORIAB", "SHROWR", "OMUECT", "INSKSA", "PESELT", "ECEVAF", "NOROOL")

        fun path(vararg cells: Pair<Int, Int>) = cells.map { (r, c) -> Coord(r, c) }

        val ACORN = path(4 to 5, 3 to 4, 2 to 3, 1 to 2, 0 to 1)
        val BRANCH = path(1 to 5, 2 to 5, 1 to 4, 0 to 5, 0 to 4, 0 to 3)
        val HUSK = path(2 to 1, 3 to 2, 4 to 2, 4 to 3)
        val LEAVES = path(5 to 4, 5 to 3, 6 to 4, 6 to 3, 6 to 2, 5 to 2)
        val MOSS = path(3 to 1, 3 to 0, 2 to 0, 1 to 0)
        val PINECONE = path(5 to 0, 4 to 0, 4 to 1, 5 to 1, 6 to 1, 7 to 1, 7 to 0, 6 to 0)
        val TWIG = path(3 to 5, 2 to 4, 1 to 3, 0 to 2)
        val SPANGRAM = path(0 to 0, 1 to 1, 2 to 2, 3 to 3, 4 to 4, 5 to 5, 6 to 5, 7 to 5, 7 to 4, 7 to 3, 7 to 2)

        val THEME = listOf(ACORN, BRANCH, HUSK, LEAVES, MOSS, PINECONE, TWIG)
        val THEME_WORDS = listOf("ACORN", "BRANCH", "HUSK", "LEAVES", "MOSS", "PINECONE", "TWIG")
        const val SPANGRAM_WORD = "FORESTFLOOR"

        // Dictionary words on the board, none crossing the theme words dragged afterwards.
        val NON_THEME = listOf(
            path(1 to 4, 2 to 5, 3 to 5, 4 to 4),
            path(1 to 5, 2 to 5, 1 to 4, 0 to 5),
            path(0 to 4, 1 to 4, 2 to 5, 3 to 5),
        )
    }
}
