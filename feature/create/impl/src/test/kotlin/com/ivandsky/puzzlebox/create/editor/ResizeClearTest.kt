package com.ivandsky.puzzlebox.create.editor

import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
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
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.CreateDraftCopy
import com.ivandsky.puzzlebox.game.DecodeError
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.GamePlugin
import com.ivandsky.puzzlebox.game.GameRegistry
import com.ivandsky.puzzlebox.game.VariantId
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.GameEditor
import com.ivandsky.puzzlebox.game.editor.IncompleteReason
import com.ivandsky.puzzlebox.game.editor.ReplaceDraft
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.gameui.GamePlayRegistry
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.gameui.editor.GameEditorRegistry
import com.ivandsky.puzzlebox.model.GameId
import com.ivandsky.puzzlebox.navigation.NavigationState
import com.ivandsky.puzzlebox.navigation.Navigator
import com.ivandsky.puzzlebox.testing.game.TestGames
import com.ivandsky.puzzlebox.testing.repository.FakeDraftRepository
import com.ivandsky.puzzlebox.testing.repository.FakePublishedPuzzleRepository
import com.ivandsky.puzzlebox.testing.repository.FakeUserPreferencesRepository
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper

/**
 * Resize and Clear from the Studio tool strip (spec A5, S3): the size popover offers exactly
 * the editor's variant table with the open size checked; a resize and a clear are each one
 * ReplaceDraft step whose undo restores the exact board, through the VM's EditHistory into
 * the board's controller and the stored row. A resize that would start a blank board over
 * content asks first ("Start a blank board?"); Cancel changes nothing, and a blank board
 * resizes at once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ResizeClearTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val ui = FakeGameEditorUi()
    private val drafts = FakeDraftRepository()
    private val navigator = Navigator(NavigationState(CreateKey, listOf(listOf(CreateKey, EditorKey(DRAFT_ID)))))

    @Before
    fun setUp() {
        runBlocking { drafts.upsert(storedRow(EditorDraft(GridEditor, DraftState(SEVEN)).encode())) }
        val games = GameRegistry(listOf(withEditor(GridEditor)))
        val editors = GameEditorRegistry(listOf(ui))
        startKoin {
            modules(
                module {
                    viewModel { (key: EditorKey) ->
                        val copyDraft = CreateDraftCopy(drafts, newId = { "copy-1" }, clock = { NOW })
                        EditorViewModel(key, drafts, games, editors, GamePlayRegistry(emptyList()), copyDraft, DefaultDispatcherProvider(), saves = CoroutineScope(SupervisorJob() + Dispatchers.Main), clock = { NOW }, createShared = signedOutPublishing(drafts), publishedPuzzles = FakePublishedPuzzleRepository(), identity = nobodySignedIn(), idTokens = noGoogleAccount, updatePublishState = signedOutUnpublishing(), preferences = FakeUserPreferencesRepository())
                    }
                    idleCreateRoot(drafts)
                },
            )
        }
        composeRule.setContent {
            PuzzleBoxTheme {
                entryProvider<NavKey> { editorEntry(navigator, PlayLayout.SinglePane) }(EditorKey(DRAFT_ID)).Content()
            }
        }
        composeRule.waitUntil { ui.controllers.isNotEmpty() }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `resize and clear are each one ReplaceDraft step to the editor's blank board`() {
        val seven = EditorDraft(GridEditor, DraftState(SEVEN))

        val resized = seven.resize(NINE)
        val cleared = seven.clear()

        assertEquals(ReplaceDraft(SEVEN, Grid(9)), resized.state.history.done.single())
        assertEquals(Grid(9), resized.state.draft)
        assertEquals(SEVEN, resized.undo()!!.state.draft)
        assertEquals(Grid(7), cleared.state.draft)
        assertEquals(SEVEN, cleared.undo()!!.state.draft)
    }

    @Test
    fun `the size popover lists exactly the editor's variants with the open size checked`() {
        composeRule.onAllNodesWithTag(POPOVER).assertCountEquals(0)

        composeRule.onNodeWithTag(SIZE).performClick()

        composeRule.onNodeWithTag(POPOVER).assertExists()
        val chips = composeRule.onAllNodes(isChip).fetchSemanticsNodes().map { it.config[SemanticsProperties.TestTag] }
        assertEquals(GridEditor.variants.map { CHIP + it.value }, chips)
        composeRule.onAllNodes(isChip and isSelected()).assertCountEquals(1)
        composeRule.onNodeWithTag(CHIP + SEVEN_ID.value).assertIsSelected()
    }

    @Test
    fun `picking another size on a board with content shows the dialog and leaves the board unchanged`() {
        pick(NINE)

        composeRule.onAllNodesWithTag(POPOVER).assertCountEquals(0)
        composeRule.onNodeWithTag(DIALOG).assertExists()
        assertEquals(SEVEN, board())
        composeRule.onNodeWithTag(UNDO).assertIsNotEnabled()
        assertTrue(controller().adopted.isEmpty())
    }

    @Test
    fun `confirming resizes 7 to 9 as one step and undo restores the exact 7x7 board`() {
        pick(NINE)
        composeRule.onNodeWithTag(CONFIRM).performClick()

        composeRule.onAllNodesWithTag(DIALOG).assertCountEquals(0)
        assertEquals(Grid(9), board())
        assertEquals(ReplaceDraft(SEVEN, Grid(9)), controller().draft.value.state.history.done.single())
        awaitStored { drafts.rows.getValue(DRAFT_ID).variant == NINE }
        composeRule.onNodeWithTag(UNDO).assertIsEnabled()

        composeRule.onNodeWithTag(UNDO).performClick()

        assertEquals(SEVEN, board())
        composeRule.onNodeWithTag(UNDO).assertIsNotEnabled()
        composeRule.onNodeWithTag(REDO).assertIsEnabled()
        awaitStored { drafts.rows.getValue(DRAFT_ID).variant == SEVEN_ID }
        assertEquals(SEVEN, GridEditor.decodeDraft(drafts.rows.getValue(DRAFT_ID).blob).getOrNull()!!.draft)
    }

    @Test
    fun `cancel closes the dialog and changes nothing`() {
        pick(NINE)
        composeRule.onNodeWithTag(CANCEL).performClick()

        composeRule.onAllNodesWithTag(DIALOG).assertCountEquals(0)
        assertEquals(SEVEN, board())
        composeRule.onNodeWithTag(UNDO).assertIsNotEnabled()
        assertTrue(controller().adopted.isEmpty())
    }

    @Test
    fun `back closes the dialog and changes nothing`() {
        pick(NINE)
        val dialog = ShadowDialog.getLatestDialog() as ComponentDialog
        composeRule.runOnUiThread { dialog.onBackPressedDispatcher.onBackPressed() }

        composeRule.onAllNodesWithTag(DIALOG).assertCountEquals(0)
        assertEquals(SEVEN, board())
        composeRule.onNodeWithTag(UNDO).assertIsNotEnabled()
        assertTrue(controller().adopted.isEmpty())
    }

    @Test
    fun `a blank board resizes at once with no dialog`() {
        composeRule.onNodeWithTag(CLEAR).performClick()
        assertEquals(Grid(7), board())

        pick(NINE)

        composeRule.onAllNodesWithTag(DIALOG).assertCountEquals(0)
        assertEquals(Grid(9), board())
    }

    @Test
    fun `the size popover shows its range hint`() {
        composeRule.onNodeWithTag(SIZE).performClick()

        composeRule.onNodeWithTag(HINT).assertExists()
    }

    @Test
    fun `clear is one step and undo restores the exact board`() {
        composeRule.onNodeWithTag(CLEAR).performClick()

        assertEquals(Grid(7), board())
        composeRule.onNodeWithTag(UNDO).assertIsEnabled()

        composeRule.onNodeWithTag(UNDO).performClick()

        assertEquals(SEVEN, board())
        composeRule.onNodeWithTag(UNDO).assertIsNotEnabled()
        assertEquals(2, controller().adopted.size)
    }

    @Test
    fun `picking the open size closes the popover without a step`() {
        composeRule.onNodeWithTag(SIZE).performClick()
        composeRule.onNodeWithTag(CHIP + SEVEN_ID.value).performClick()

        composeRule.onAllNodesWithTag(POPOVER).assertCountEquals(0)
        assertEquals(SEVEN, board())
        composeRule.onNodeWithTag(UNDO).assertIsNotEnabled()
        assertTrue(controller().adopted.isEmpty())
    }

    private fun pick(size: VariantId) {
        composeRule.onNodeWithTag(SIZE).performClick()
        composeRule.onNodeWithTag(CHIP + size.value).performClick()
        composeRule.waitForIdle()
    }

    private fun controller(): FakeEditorController = ui.controllers.single()

    private fun board(): Grid {
        composeRule.waitForIdle()
        return controller().draft.value.state.draft as Grid
    }

    /** Waits for the autosave's 300 ms window to store the row, running the paused main looper's clock. */
    private fun awaitStored(condition: () -> Boolean) {
        composeRule.waitUntil {
            ShadowLooper.idleMainLooper(FRAME_MS, TimeUnit.MILLISECONDS)
            condition()
        }
    }

    /** A stand-in board with real sizes: its side and the marked cells. */
    private data class Grid(val size: Int, val marks: Set<Int> = emptySet())

    /**
     * A stand-in game whose variant table is the sides 4..12 (FakeGameEditor has one size).
     * Its codec is `[version, side, marks…]`; history isn't stored, as no test reopens a draft.
     */
    private object GridEditor : GameEditor<Grid> {
        override val gameId: GameId = GameId("fake")
        override val variants: List<VariantId> = (4..12).map(::variantOf)
        override val draftFormatVersion: Int = 1

        override fun newDraft(variant: VariantId): Grid = Grid(variant.value.substringAfter('/').toInt())

        override fun variantOf(draft: Grid): VariantId = variantOf(draft.size)

        override fun encodeDraft(state: DraftState<Grid>): ByteArray =
            byteArrayOf(draftFormatVersion.toByte(), state.draft.size.toByte()) +
                state.draft.marks.sorted().map(Int::toByte)

        override fun decodeDraft(bytes: ByteArray): DecodeResult<DraftState<Grid>> = when {
            bytes.size < 2 -> DecodeResult.Err(DecodeError.Malformed("grid draft is at least 2 bytes"))
            bytes[0].toInt() != draftFormatVersion ->
                DecodeResult.Err(DecodeError.UnsupportedVersion(bytes[0].toInt(), draftFormatVersion))
            else -> DecodeResult.Ok(DraftState(Grid(bytes[1].toInt(), bytes.drop(2).map(Byte::toInt).toSet())))
        }

        override fun analyze(draft: Grid, budget: Budget): Verdict =
            if (draft.marks.isEmpty()) Verdict.Incomplete(IncompleteReason("grid.blank", draft.size * draft.size))
            else Verdict.Unique(Solution(draft.marks.sorted()))

        fun variantOf(size: Int): VariantId = VariantId("${gameId.value}/$size")
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-06T10:00:00Z")
        const val DRAFT_ID = "draft-1"
        const val FRAME_MS = 16L
        const val SIZE = "create.tool.size"
        const val CLEAR = "create.tool.clear"
        const val UNDO = "create.tool.undo"
        const val REDO = "create.tool.redo"
        const val POPOVER = "create.size.popover"
        const val CHIP = "create.size.chip."
        const val HINT = "create.size.hint"
        const val DIALOG = "create.resize.dialog"
        const val CONFIRM = "create.resize.confirm"
        const val CANCEL = "create.resize.cancel"

        /** A painted 7×7: three marked cells. */
        val SEVEN = Grid(7, setOf(0, 8, 48))
        val SEVEN_ID = GridEditor.variantOf(7)
        val NINE = GridEditor.variantOf(9)

        val isChip = SemanticsMatcher("a size chip") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(CHIP) == true
        }

        fun storedRow(blob: ByteArray) = Draft(
            id = DRAFT_ID,
            gameId = GridEditor.gameId,
            variant = SEVEN_ID,
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

        fun withEditor(editor: GameEditor<*>): GamePlugin<*, *, *> {
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
