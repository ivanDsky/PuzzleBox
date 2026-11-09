package com.ivandsky.puzzlebox.create.editor

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import com.ivandsky.puzzlebox.common.dispatcher.DefaultDispatcherProvider
import com.ivandsky.puzzlebox.create.FakeEditorController
import com.ivandsky.puzzlebox.create.FakeGameEditorUi
import com.ivandsky.puzzlebox.create.api.CreateKey
import com.ivandsky.puzzlebox.create.api.EditorKey
import com.ivandsky.puzzlebox.create.navigation.editorEntry
import com.ivandsky.puzzlebox.data.model.Draft
import com.ivandsky.puzzlebox.data.model.TestSolve
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.CreateDraftCopy
import com.ivandsky.puzzlebox.game.CompletionRecord
import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.game.PuzzleRef
import com.ivandsky.puzzlebox.game.RecordStats
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.DraftExport
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.GameEditor
import com.ivandsky.puzzlebox.game.editor.IncompleteReason
import com.ivandsky.puzzlebox.game.editor.ReplaceDraft
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.GamePlayController
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.GamePlayUi
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.Restored
import com.ivandsky.puzzlebox.gameui.TutorialStep
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.model.PuzzleKey
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeCompletionRepository
import com.ivandsky.puzzlebox.testing.repository.FakeDraftRepository
import com.ivandsky.puzzlebox.testing.repository.FakePublishedPuzzleRepository
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * The required test solve (spec A10, 00 §3): winning the sandbox stores the test on the draft
 * and writes no completion record; a board edit silently invalidates it and shows the
 * `create.toast.testCleared` toast, whose Undo makes it valid again; a title edit and an
 * aid-only edit (stripped by [GameEditor.layoutOf], like Queens answer crowns) keep it; and the
 * sandbox plays the plain [DraftExport.toPuzzle] puzzle, so no aid reaches it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TestSolveSessionTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val editor = AidEditor()
    private val ui = FakeGameEditorUi(editor.gameId)
    private val play = SandboxPlayUi(editor.gameId)
    private val drafts = FakeDraftRepository()
    private val completions = FakeCompletionRepository()
    private val navigator = Navigator(NavigationState(CreateKey, listOf(listOf(CreateKey, EditorKey(DRAFT_ID)))))
    private lateinit var vm: EditorViewModel

    private val opened = editor.draft(AidDraft(marks = 2, aid = 7))

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `winning the sandbox stores the test on the draft and writes no completion record`() {
        open(opened)

        startTestSolve()
        val sandbox = play.controllers.single()
        assertEquals(PuzzleKey.Custom("draft-$DRAFT_ID"), sandbox.ref?.key)
        composeRule.runOnIdle { sandbox.win(timeMs = 102_000, hints = 1) }

        val test = TestSolve(opened.layoutKey, timeMs = 102_000, hints = 1)
        awaitTrue { drafts.rows[DRAFT_ID]?.testSolve == test }
        assertTrue("no record, streak or board entry", runBlocking { completions.observeRecords().first() }.isEmpty())
        composeRule.onNodeWithTag(SOLVED).assertExists()

        composeRule.onNodeWithTag(DONE).performClick()
        awaitTrue { composeRule.onAllNodes(hasTestTag(ACTION_TEST_SOLVE)).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(test, vm.testSolve.passed.value)
    }

    @Test
    fun `a board edit silently clears the test with a toast, and the toast's undo restores it`() {
        val test = passTest(opened)

        val edited = opened.apply(ReplaceDraft(opened.state.draft, AidDraft(marks = 3, aid = 7)))
        composeRule.runOnIdle { controller.edit(edited) }

        awaitTrue { composeRule.onAllNodes(hasTestTag(TOAST)).fetchSemanticsNodes().isNotEmpty() }
        assertNull("the edited layout has no valid test", vm.testSolve.passed.value)
        awaitTrue { drafts.rows[DRAFT_ID]?.blob?.contentEquals(edited.encode()) == true }
        assertEquals("the clear is silent: the stored test stays", test, drafts.rows[DRAFT_ID]?.testSolve)

        composeRule.onNode(hasAnyAncestor(hasTestTag(TOAST)) and hasClickAction()).performClick()

        awaitTrue { vm.testSolve.passed.value == test }
        assertEquals(opened.state.draft, (controller.draft.value.state.draft))
    }

    @Test
    fun `restoring the test rewinds past aid-only edits made after the clearing edit`() {
        val test = passTest(opened)
        val edited = opened.apply(ReplaceDraft(opened.state.draft, AidDraft(marks = 3, aid = 7)))
        composeRule.runOnIdle { controller.edit(edited) }
        val aided = edited.apply(ReplaceDraft(edited.state.draft, AidDraft(marks = 3, aid = 9)))
        composeRule.runOnIdle { controller.edit(aided) }
        awaitTrue { controller.draft.value.state.draft == aided.state.draft }

        composeRule.runOnIdle { vm.restoreTest() }

        awaitTrue { vm.testSolve.passed.value == test }
        assertEquals(opened.state.draft, controller.draft.value.state.draft)
    }

    @Test
    fun `any later board change dismisses the toast`() {
        passTest(opened)
        val edited = opened.apply(ReplaceDraft(opened.state.draft, AidDraft(marks = 3, aid = 7)))
        composeRule.runOnIdle { controller.edit(edited) }
        awaitTrue { composeRule.onAllNodes(hasTestTag(TOAST)).fetchSemanticsNodes().isNotEmpty() }

        composeRule.runOnIdle { controller.edit(edited.apply(ReplaceDraft(edited.state.draft, AidDraft(marks = 3, aid = 9)))) }

        awaitTrue { composeRule.onAllNodes(hasTestTag(TOAST)).fetchSemanticsNodes().isEmpty() }
        assertFalse(vm.testSolve.cleared.value)
    }

    @Test
    fun `a toolbar undo back to the tested layout passes the test and dismisses the toast`() {
        val test = passTest(opened)
        composeRule.runOnIdle { controller.edit(opened.apply(ReplaceDraft(opened.state.draft, AidDraft(marks = 3, aid = 7)))) }
        awaitTrue { composeRule.onAllNodes(hasTestTag(TOAST)).fetchSemanticsNodes().isNotEmpty() }

        composeRule.runOnIdle { vm.undo() }

        awaitTrue { composeRule.onAllNodes(hasTestTag(TOAST)).fetchSemanticsNodes().isEmpty() }
        assertEquals(test, vm.testSolve.passed.value)
    }

    @Test
    fun `the toast times out`() {
        passTest(opened)
        composeRule.runOnIdle { controller.edit(opened.apply(ReplaceDraft(opened.state.draft, AidDraft(marks = 3, aid = 7)))) }
        awaitTrue { composeRule.onAllNodes(hasTestTag(TOAST)).fetchSemanticsNodes().isNotEmpty() }

        composeRule.mainClock.advanceTimeBy(TOAST_TIMEOUT_MS)

        awaitTrue { composeRule.onAllNodes(hasTestTag(TOAST)).fetchSemanticsNodes().isEmpty() }
        assertFalse(vm.testSolve.cleared.value)
    }

    @Test
    fun `a title edit keeps the test`() {
        val test = passTest(opened)

        composeRule.runOnIdle { vm.meta.rename("Castle") }

        awaitTrue { drafts.rows[DRAFT_ID]?.title == "Castle" }
        assertEquals(test, drafts.rows[DRAFT_ID]?.testSolve)
        assertEquals(test, vm.testSolve.passed.value)
        composeRule.onNodeWithTag(TOAST).assertDoesNotExist()
    }

    @Test
    fun `an aid-only edit keeps the test with no toast, and no aid reaches the sandbox`() {
        open(opened)
        startTestSolve()
        assertEquals(
            "the sandbox plays toPuzzle's plain puzzle",
            editor.toPuzzle(AidDraft(marks = 2), Solution(listOf(2))),
            play.controllers.single().puzzle,
        )
        val test = finishTestSolve(opened)

        val aided = opened.apply(ReplaceDraft(opened.state.draft, AidDraft(marks = 2, aid = 9)))
        composeRule.runOnIdle { controller.edit(aided) }

        awaitTrue { drafts.rows[DRAFT_ID]?.blob?.contentEquals(aided.encode()) == true }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TOAST).assertDoesNotExist()
        assertEquals(test, vm.testSolve.passed.value)
    }

    @Test
    fun `test solve is disabled until the board is unique`() {
        open(editor.draft(AidDraft()))

        awaitTrue { (vm.state.value as? EditorUiState.Ready)?.verdict is Verdict.Incomplete }
        composeRule.onNodeWithTag(ACTION_TEST_SOLVE).assertIsNotEnabled()
    }

    @Test
    fun `while an edit is checked, test solve and the readiness line keep the settled verdict`() {
        open(opened)
        awaitTrue { composeRule.onAllNodes(hasTestTag(ACTION_TEST_SOLVE) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }

        composeRule.runOnIdle { controller.edit(opened.apply(ReplaceDraft(opened.state.draft, AidDraft(marks = 3, aid = 7)))) }
        awaitTrue { (vm.state.value as? EditorUiState.Ready)?.verdict == Verdict.Checking }
        composeRule.mainClock.advanceTimeByFrame()

        assertEquals("still Checking", Verdict.Checking, (vm.state.value as? EditorUiState.Ready)?.verdict)
        composeRule.onNodeWithTag(ACTION_TEST_SOLVE).assertIsEnabled()
        composeRule.onNodeWithTag(NOT_UNIQUE_ENTRY).assertDoesNotExist()
    }

    private val controller: FakeEditorController get() = ui.controllers.single()

    /** Opens [draft], test solves it and returns the stored test. */
    private fun passTest(draft: EditorDraft<AidDraft>): TestSolve {
        open(draft)
        startTestSolve()
        return finishTestSolve(draft)
    }

    private fun startTestSolve() {
        val enabled = hasTestTag(ACTION_TEST_SOLVE) and isEnabled()
        awaitTrue { composeRule.onAllNodes(enabled).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNode(enabled).performClick()
        awaitTrue { composeRule.onAllNodes(hasTestTag(SANDBOX)).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithTag(PLAY_BOARD).assertExists()
    }

    /** Wins the open sandbox and goes back to the editor; returns the stored test. */
    private fun finishTestSolve(draft: EditorDraft<AidDraft>): TestSolve {
        composeRule.runOnIdle { play.controllers.last().win(timeMs = 61_000, hints = 0) }
        val test = TestSolve(draft.layoutKey, timeMs = 61_000, hints = 0)
        awaitTrue { drafts.rows[DRAFT_ID]?.testSolve == test }
        composeRule.onNodeWithTag(DONE).performClick()
        awaitTrue { composeRule.onAllNodes(hasTestTag(SANDBOX)).fetchSemanticsNodes().isEmpty() }
        assertEquals(test, vm.testSolve.passed.value)
        return test
    }

    private fun open(draft: EditorDraft<AidDraft>) {
        runBlocking { drafts.upsert(storedRow(draft.encode())) }
        val games = GameRegistry(listOf(withEditor(editor)))
        val editors = GameEditorRegistry(listOf(ui))
        val plays = GamePlayRegistry(listOf(play))
        val copyDraft = CreateDraftCopy(drafts, newId = { "copy-1" }, clock = { NOW })
        startKoin {
            modules(
                module {
                    viewModel { (key: EditorKey) ->
                        EditorViewModel(key, drafts, games, editors, plays, copyDraft, DefaultDispatcherProvider(), saves = CoroutineScope(SupervisorJob() + Dispatchers.Main), clock = { NOW }, createShared = signedOutPublishing(drafts), publishedPuzzles = FakePublishedPuzzleRepository(), identity = nobodySignedIn(), idTokens = noGoogleAccount, updatePublishState = signedOutUnpublishing())
                            .also { vm = it }
                    }
                    idleCreateRoot(drafts)
                },
            )
        }
        val entries = entryProvider<NavKey> { editorEntry(navigator, PlayLayout.SinglePane) }
        composeRule.setContent {
            PuzzleBoxTheme {
                val top = navigator.state.currentStack.last()
                key(top) { entries(top).Content() }
            }
        }
        composeRule.waitUntil(TIMEOUT_MS) { ui.controllers.isNotEmpty() }
    }

    /** Waits for [condition], running the paused main looper so view-model coroutines progress. */
    private fun awaitTrue(condition: () -> Boolean) {
        composeRule.waitUntil(TIMEOUT_MS) {
            ShadowLooper.idleMainLooper(FRAME_MS, TimeUnit.MILLISECONDS)
            condition()
        }
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-06T10:00:00Z")
        const val DRAFT_ID = "draft-1"
        const val TIMEOUT_MS = 5_000L
        const val FRAME_MS = 16L
        const val ACTION_TEST_SOLVE = "create.action.testSolve"
        const val SANDBOX = "create.testSolve.screen"
        const val SOLVED = "create.testSolve.solved"
        const val DONE = "create.testSolve.done"
        const val TOAST = "create.toast.testCleared"
        const val PLAY_BOARD = "sandbox.play.board"
        const val NOT_UNIQUE_ENTRY = "create.readiness.not-unique"

        /** Past SnackbarDuration.Long. */
        const val TOAST_TIMEOUT_MS = 11_000L

        fun storedRow(blob: ByteArray) = Draft(
            id = DRAFT_ID,
            gameId = AID_GAME,
            variant = AID_VARIANT,
            draftFormatVersion = 1,
            blob = blob,
            title = "Untitled 1",
            updatedAt = NOW,
            testSolve = null,
            reservedCode = null,
            imported = false,
            authorName = "",
            note = "",
        )

        fun withEditor(editor: AidEditor): GamePlugin<*, *, *> {
            val base = TestGames.plugin(editor.gameId)
            return GamePlugin(
                definition = base.definition,
                rules = base.rules,
                session = base.session,
                score = base.score,
                stateOf = base.stateOf,
                initialSession = base.initialSession,
                refOf = base.refOf,
                replaySession = base.replaySession,
                rendererModel = base.rendererModel,
                editor = editor,
            )
        }
    }
}

private val AID_GAME = GameId("aid")
private val AID_VARIANT = VariantId("aid/v1")

/** A board of [marks] plus an author-only [aid] that, like a Queens answer crown, never reaches the puzzle. */
private data class AidDraft(val marks: Int = 0, val aid: Int = 0)

/**
 * An exporting editor whose [layoutOf] strips [AidDraft.aid]: a board with marks is Unique with
 * the mark count as its solution; [toPuzzle] writes the marks alone.
 */
private class AidEditor : GameEditor<AidDraft>, DraftExport<AidDraft> {
    override val gameId: GameId = AID_GAME
    override val variants: List<VariantId> = listOf(AID_VARIANT)
    override val draftFormatVersion: Int = 1

    override fun newDraft(variant: VariantId): AidDraft = AidDraft()

    override fun variantOf(draft: AidDraft): VariantId = AID_VARIANT

    override fun encodeDraft(state: DraftState<AidDraft>): ByteArray =
        byteArrayOf(draftFormatVersion.toByte(), state.draft.marks.toByte(), state.draft.aid.toByte())

    override fun decodeDraft(bytes: ByteArray): DecodeResult<DraftState<AidDraft>> = when {
        bytes.size != 3 -> DecodeResult.Err(DecodeError.Malformed("aid draft is 3 bytes"))
        bytes[0].toInt() != draftFormatVersion ->
            DecodeResult.Err(DecodeError.UnsupportedVersion(bytes[0].toInt(), draftFormatVersion))
        else -> DecodeResult.Ok(DraftState(AidDraft(bytes[1].toInt(), bytes[2].toInt())))
    }

    override fun analyze(draft: AidDraft, budget: Budget): Verdict =
        if (draft.marks == 0) Verdict.Incomplete(IncompleteReason("aid.blank", 1))
        else Verdict.Unique(Solution(listOf(draft.marks)))

    override fun layoutOf(draft: AidDraft): AidDraft = draft.copy(aid = 0)

    override fun toPuzzle(draft: AidDraft, solution: Solution): Puzzle =
        Puzzle(gameId, AID_VARIANT, 1, byteArrayOf(TestGames.MAGIC, draft.marks.toByte()))

    fun draft(board: AidDraft): EditorDraft<AidDraft> = EditorDraft(this, DraftState(board))
}

/** The game's play seam for the sandbox: a bare board and controllers the test wins by hand. */
private class SandboxPlayUi(override val gameId: GameId) : GamePlayUi {

    val controllers = mutableListOf<SandboxController>()

    override val tutorial: List<TutorialStep> = emptyList()

    override fun createController(env: PlayEnvironment): GamePlayController =
        SandboxController(env).also { controllers += it }

    @Composable
    override fun Board(controller: GamePlayController, modifier: Modifier) {
        Box(modifier.testTag("sandbox.play.board"))
    }

    @Composable
    override fun Screen(
        controller: GamePlayController,
        status: PlayBoardStatus,
        layout: PlayLayout,
        sidePanel: (@Composable ColumnScope.() -> Unit)?,
        onRetry: () -> Unit,
        onSolved: (timeMs: Long, hintsUsed: Int) -> Unit,
        modifier: Modifier,
    ) = Unit
}

/** Records what it adopted; [win] emits the completion record a real game emits on its win. */
private class SandboxController(private val env: PlayEnvironment) : GamePlayController {
    var puzzle: Puzzle? = null
    var ref: PuzzleRef? = null

    override val isComplete: Boolean get() = false

    override fun adoptToday(puzzle: Puzzle, ref: PuzzleRef): Adopt {
        this.puzzle = puzzle
        this.ref = ref
        return Adopt.Adopted
    }

    override fun adoptRestored(bytes: ByteArray): Restored? = null

    override fun replay() = Unit

    fun win(timeMs: Long, hints: Int) {
        env.onWon(CompletionRecord(ref!!, time = timeMs, hints = hints, stats = RecordStats(1, "")))
    }
}
