package com.ivandsky.puzzlebox.app.create

import android.content.Context
import android.provider.Settings
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.create.editor.ActionArea
import com.ivandsky.puzzlebox.create.editor.PublishDetails
import com.ivandsky.puzzlebox.create.editor.PublishSession
import com.ivandsky.puzzlebox.create.editor.StudioScreen
import com.ivandsky.puzzlebox.create.editor.ToolSlot
import com.ivandsky.puzzlebox.create.editor.ToolStrip
import com.ivandsky.puzzlebox.create.editor.UndoRedoButtons
import com.ivandsky.puzzlebox.data.model.TestSolve
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.domain.ValidatePublish
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraft
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraftAnalyzer
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraftPath
import com.ivandsky.puzzlebox.games.strands.editor.StrandsEditor
import com.ivandsky.puzzlebox.games.strandsui.editor.StrandsEditorController
import com.ivandsky.puzzlebox.games.strandsui.editor.StrandsEditorUi
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Device check D8 (pass 2): the phone Studio keeps every Strands row in the board slot, so
 * none needs a scroll the trace would take. The window is the emulator's 411x914 dp phone
 * with its status bar over the Studio and the tab and navigation bars under it. The route's
 * [ToolSlot] hosts the tools; a passed test opens the publish details in their place until
 * Edit board. The keyboard is the IME inset dispatched to the compose root.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, qualifiers = "w411dp-h914dp-420dpi")
class StrandsStudioPhoneBoardTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var view: View
    private var editBoard by mutableStateOf(false)

    @Test
    fun `an untested draft shows every row inside the board slot`() {
        show(tested = false)

        assertEveryRowInTheSlot()
        assertWordFieldInTheSlot()
    }

    @Test
    fun `the details show every row, and Edit board gives the board the tools' height back`() {
        show(tested = true)
        rule.onNodeWithTag(DETAILS).assertExists()
        assertEveryRowInTheSlot()
        val withDetails = slot()

        rule.onNodeWithTag(EDIT_BOARD).performClick()
        rule.mainClock.advanceTimeBy(SETTLE_MS)

        rule.onNodeWithTag(STRIP).assertExists()
        val editing = slot()
        assertTrue("Edit board slot $editing no taller than the details' $withDetails", editing.height > withDetails.height)
        val tools = rule.onNodeWithTag(TOOL_SLOT).getUnclippedBoundsInRoot()
        val strip = rule.onNodeWithTag(STRIP).getUnclippedBoundsInRoot()
        assertTrue("tool slot $tools holds more than the tools $strip", tools.height <= strip.height + 1.dp)
        assertEveryRowInTheSlot()
        assertWordFieldInTheSlot()
    }

    @Test
    fun `the keyboard opened and closed over the details leaves the board as it was`() {
        show(tested = true)
        val before = slot() to rule.onNodeWithTag(GRID).getUnclippedBoundsInRoot()

        keyboard(320)
        keyboard(0)

        val after = slot() to rule.onNodeWithTag(GRID).getUnclippedBoundsInRoot()
        assertTrue("slot and board $after after the keyboard, $before before", after == before)
    }

    /** Every cell of the first and the last row inside the board slot, and the slot inside the window. */
    private fun assertEveryRowInTheSlot() {
        val slot = slot()
        val window = rule.onRoot().getUnclippedBoundsInRoot()
        assertTrue("slot $slot outside the window $window", slot.top >= window.top && slot.bottom <= window.bottom)
        val edgeRows = (0 until StrandsDraft.COLS) + ((StrandsDraft.ROWS - 1) * StrandsDraft.COLS until StrandsDraft.ROWS * StrandsDraft.COLS)
        edgeRows.forEach { idx ->
            val cell = rule.onNodeWithTag("strands.editor.cell.$idx").getUnclippedBoundsInRoot()
            assertTrue("cell $idx $cell below the slot $slot", cell.bottom <= slot.bottom + 0.5.dp)
            assertTrue("cell $idx $cell above the slot $slot", cell.top >= slot.top - 0.5.dp)
        }
    }

    /** The word field, which names a traced path, starts inside the board slot: in reach, not under the tools. */
    private fun assertWordFieldInTheSlot() {
        val slot = slot()
        val field = rule.onNodeWithTag(WORD).getUnclippedBoundsInRoot()
        assertTrue("word field $field outside the slot $slot", field.top >= slot.top && field.top + FIELD_PEEK <= slot.bottom)
    }

    private fun slot(): DpRect = rule.onNodeWithTag(BOARD_SLOT).getUnclippedBoundsInRoot()

    private fun keyboard(heightDp: Int) {
        val px = with(rule.density) { heightDp.dp.roundToPx() }
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, px))
            .setVisible(WindowInsetsCompat.Type.ime(), px > 0)
            .build()
        rule.runOnUiThread { ViewCompat.dispatchApplyWindowInsets(view, insets) }
        rule.mainClock.advanceTimeBy(SETTLE_MS)
    }

    private fun show(tested: Boolean) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        val letters = WORDS.joinToString("")
        val paths = WORDS.mapIndexed { row, word -> StrandsDraftPath(List(StrandsDraft.COLS) { row * StrandsDraft.COLS + it }, word) }
        val draft = StrandsDraft(letters, CLUE, paths, SPANGRAM)
        val verdict = StrandsDraftAnalyzer.analyze(draft, Budget.Unlimited)
        assertTrue("expected Unique, got $verdict", verdict is Verdict.Unique)
        val editorDraft = EditorDraft(StrandsEditor, DraftState(draft))
        val controller = StrandsEditorController(editorDraft) {}
        val passed = TestSolve(editorDraft.layoutKey, timeMs = 42_000, hints = 0).takeIf { tested }
        rule.mainClock.autoAdvance = false
        rule.setContent {
            view = LocalView.current
            PuzzleBoxTheme(darkTheme = true) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    // The emulator's status bar above the Studio; its tab and navigation bars below.
                    Box(Modifier.fillMaxSize().padding(top = STATUS_BAR, bottom = BOTTOM_CHROME)) {
                        Studio(editorDraft, controller, verdict, passed)
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(SETTLE_MS)
    }

    @Composable
    private fun Studio(editorDraft: EditorDraft<*>, controller: StrandsEditorController, verdict: Verdict, passed: TestSolve?) {
        val layout = PlayLayout.SinglePane
        val publish = PublishSession.State(signedIn = false).takeIf { passed != null }
        StudioScreen(
            title = TITLE,
            subtitle = "Strands ${StrandsEditorUi.sizeLabel(editorDraft.variant)}",
            saving = false,
            verdict = verdict,
            overlay = controller.overlay.collectAsState().value,
            layout = layout,
            onBack = {},
            onRename = {},
            onDuplicate = {},
            onDelete = {},
            board = { boardModifier -> StrandsEditorUi.Board(controller, verdict, layout, boardModifier) },
            toolStrip = { slotModifier ->
                ToolSlot(
                    open = passed != null,
                    editBoard = editBoard,
                    onEditBoard = { editBoard = true },
                    tools = { stripModifier ->
                        ToolStrip(
                            canUndo = true,
                            canRedo = false,
                            onUndo = {},
                            onRedo = {},
                            sizes = StrandsEditor.variants,
                            size = editorDraft.variant,
                            sizeLabel = StrandsEditorUi::sizeLabel,
                            onResize = {},
                            onClear = {},
                            hint = { hintModifier -> StrandsEditorUi.ToolHint(controller, hintModifier) },
                            modifier = stripModifier,
                        )
                    },
                    form = { formModifier, editBoardChip ->
                        PublishDetails(
                            title = TITLE,
                            details = PublishSession.Details(),
                            signedIn = false,
                            onRename = {},
                            onAuthorName = {},
                            onNote = {},
                            modifier = formModifier,
                            paired = true,
                            action = editBoardChip,
                            history = { UndoRedoButtons(canUndo = true, canRedo = false, onUndo = {}, onRedo = {}) },
                            fields = { fieldsModifier -> StrandsEditorUi.PublishFields(controller, true, fieldsModifier) },
                        )
                    },
                    modifier = slotModifier,
                )
            },
            actionArea = { areaModifier ->
                ActionArea(
                    passed = passed,
                    canTestSolve = editorDraft.canTestSolve,
                    onTestSolve = {},
                    blocks = ValidatePublish()(editorDraft, verdict, TITLE, passed),
                    modifier = areaModifier,
                    publish = publish,
                    title = TITLE,
                    onShowDetails = { editBoard = false }.takeIf { editBoard },
                    fieldCopy = StrandsEditorUi.publishFieldCopy,
                )
            },
        )
    }

    private companion object {
        const val SETTLE_MS = 2_000L
        const val TITLE = "Untitled Strands"
        const val CLUE = "Rainbow shades"
        const val SPANGRAM = 5
        const val BOARD_SLOT = "create.editor.board"
        const val TOOL_SLOT = "create.tool.slot"
        const val STRIP = "create.tool.strip"
        const val DETAILS = "create.details"
        const val EDIT_BOARD = "create.tool.editBoard"
        const val GRID = "strands.editor.board"
        const val WORD = "strands.editor.word"
        val STATUS_BAR = 24.dp
        val BOTTOM_CHROME = 128.dp

        /** A text field's least height (Material's 56 dp): the word field, enough of it to tap. */
        val FIELD_PEEK = 56.dp

        /** One word per row: the only tiling is the rows themselves. */
        val WORDS = listOf("SPRING", "PLANET", "HOLLOW", "BRIGHT", "FLOWER", "WINDOW", "CASTLE", "QUIVER")
    }
}
