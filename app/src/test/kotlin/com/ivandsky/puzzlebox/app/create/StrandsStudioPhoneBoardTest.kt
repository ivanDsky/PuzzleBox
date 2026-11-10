package com.ivandsky.puzzlebox.app.create

import android.content.Context
import android.provider.Settings
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.animation.SharedTransitionLayout
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
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Dp
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
import kotlin.math.abs

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
    private lateinit var controller: StrandsEditorController
    private var editBoard by mutableStateOf(false)

    @Test
    fun `an untested draft shows every row inside the board slot`() {
        show(tested = false)

        assertEveryRowInTheSlot()
        assertWordFieldInTheSlot()
    }

    @Test
    fun `a path just traced keeps its word field inside the board slot`() {
        traceTheLastRow()

        assertTrue("the traced path is not selected", controller.selectedPath.value != null)
        assertWordFieldInTheSlot()
    }

    @Test
    fun `a word longer than the path keeps its fit line inside the board slot`() {
        traceTheLastRow()
        rule.runOnIdle { controller.typeWord(WORDS.last() + WORDS.last()) }
        rule.mainClock.advanceTimeBy(SETTLE_MS)

        rule.onNodeWithTag(WORD_FIT, useUnmergedTree = true).assertExists()
        assertWordFieldInTheSlot()
    }

    @Test
    fun `selecting a path keeps the board's size`() {
        show(tested = false, traced = StrandsDraft.ROWS - 1)
        val idle = drawn(GRID)

        traceTheLastRow()

        assertTrue("the traced path is not selected", controller.selectedPath.value != null)
        val selected = drawn(GRID)
        assertTrue("the board went from $idle to $selected on the selection", abs((selected.height - idle.height).value) < 0.5f)
    }

    @Test
    fun `a path just traced can be marked the spangram from the phone`() {
        traceTheLastRow()
        val path = controller.selectedPath.value
        assertTrue("the traced path is not selected", path != null)

        val toggle = rule.onNodeWithTag("strands.editor.spangram.$path").assertIsDisplayed().getUnclippedBoundsInRoot()
        val tools = rule.onNodeWithTag(TOOL_SLOT).getUnclippedBoundsInRoot()
        val window = rule.onRoot().getUnclippedBoundsInRoot()
        assertTrue("spangram toggle $toggle outside the tool slot $tools", toggle.height > 0.dp && toggle.top >= tools.top && toggle.bottom <= tools.bottom)
        assertTrue("spangram toggle $toggle outside the window $window", toggle.bottom <= window.bottom)
        rule.onNodeWithTag("strands.editor.spangram.$path").performClick()
        rule.mainClock.advanceTimeBy(SETTLE_MS)

        assertTrue("path $path is not the spangram", (controller.draft.value.state.draft as StrandsDraft).spangram == path)
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-420dpi")
    fun `on a 360x780 phone Edit board shows the word field whole and typeable`() {
        show(tested = true)
        rule.onNodeWithTag(EDIT_BOARD).performClick()
        rule.mainClock.advanceTimeBy(SETTLE_MS)
        rule.onNodeWithTag("strands.editor.cell.0").performClick()
        rule.mainClock.advanceTimeBy(SETTLE_MS)
        assertTrue("path 0 is not selected", controller.selectedPath.value == 0)
        assertEveryRowInTheSlot()
        assertWordFieldInTheSlot()
        rule.onNodeWithTag(WORD).performTextReplacement("SPRINGS")
        rule.mainClock.advanceTimeBy(SETTLE_MS)
        assertTrue("the field took ${controller.word.value}", controller.word.value == "SPRINGS")
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-420dpi")
    fun `on a 360x780 phone an untested board has cells of at least 30dp`() {
        assertCells(tested = false, floor = 30.dp)
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-420dpi")
    fun `on a 360x780 phone Edit board after a test solve has cells of at least 30dp`() {
        assertCells(tested = true, floor = 30.dp)
    }

    @Test
    fun `on a 411x914 phone an untested board keeps its 46_5dp cells`() {
        assertCells(tested = false, floor = 46.5.dp)
    }

    @Test
    fun `on a 411x914 phone Edit board after a test solve keeps its 35_67dp cells`() {
        assertCells(tested = true, floor = 35.67.dp)
    }

    /** The settled cell height, untested or in Edit board after a passed test, at least [floor]. */
    private fun assertCells(tested: Boolean, floor: Dp) {
        show(tested = tested)
        if (tested) {
            rule.onNodeWithTag(EDIT_BOARD).performClick()
            rule.mainClock.advanceTimeBy(SETTLE_MS)
            rule.onNodeWithTag(STRIP).assertExists()
        }
        val cell = rule.onNodeWithTag("strands.editor.cell.0").getUnclippedBoundsInRoot().height
        assertTrue("cells $cell under $floor (tested: $tested)", cell >= floor - 0.01.dp)
    }

    /** The Studio with every row but the last a path, then the last row traced by taps. */
    private fun traceTheLastRow() {
        if (!::controller.isInitialized) show(tested = false, traced = StrandsDraft.ROWS - 1)
        val lastRow = (StrandsDraft.ROWS - 1) * StrandsDraft.COLS until StrandsDraft.ROWS * StrandsDraft.COLS
        lastRow.forEach { rule.onNodeWithTag("strands.editor.cell.$it").performClick() }
        rule.onNodeWithTag("strands.editor.cell.${lastRow.last}").performClick()
        rule.mainClock.advanceTimeBy(SETTLE_MS)
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
    fun `Edit board to the details shrinks the board through the slot's animation`() {
        show(tested = true, motion = true)
        rule.onNodeWithTag(EDIT_BOARD).performClick()
        rule.mainClock.advanceTimeBy(SETTLE_MS)
        val editing = drawn(GRID)

        rule.runOnIdle { editBoard = false }
        val frames = framesOfTheSwap()

        assertTheBoardFollowsTheSlot(frames, from = editing.height, to = frames.last().first.height)
    }

    @Test
    fun `the details to Edit board grow the board through the slot's animation`() {
        show(tested = true, motion = true)
        val details = drawn(GRID)

        rule.onNodeWithTag(EDIT_BOARD).performClick()
        val frames = framesOfTheSwap()

        assertTheBoardFollowsTheSlot(frames, from = details.height, to = frames.last().first.height)
    }

    @Test
    fun `a tap mid-way through the swap lands on the cell drawn under it`() {
        show(tested = true, motion = true)
        rule.onNodeWithTag(EDIT_BOARD).performClick()
        repeat(MID_SWAP_FRAMES) { rule.mainClock.advanceTimeByFrame() }
        val laidOut = rule.onNodeWithTag(GRID).getUnclippedBoundsInRoot()
        val drawn = drawn(GRID)
        assertTrue("the grid is not scaled mid-way: drawn $drawn, laid out $laidOut", abs((drawn.height - laidOut.height).value) > 1f)

        // The pointer goes through the scaled layer to the cell's drawn centre: the last cell, of the last path.
        rule.onNodeWithTag("strands.editor.cell.$LAST_CELL").performTouchInput { click(center) }
        rule.mainClock.advanceTimeBy(SETTLE_MS)

        assertTrue("the tap selected ${controller.selectedPath.value}", controller.selectedPath.value == StrandsDraft.ROWS - 1)
    }

    /** The grid and the board slot each frame, from the swap's first frame until well after the slot's animation. */
    private fun framesOfTheSwap(): List<Pair<DpRect, DpRect>> = List(SWAP_FRAMES) {
        rule.mainClock.advanceTimeByFrame()
        drawn(GRID) to slot()
    }

    /** The node's bounds as drawn, its layers' scale included: the unclipped bounds keep the unscaled size. */
    private fun drawn(tag: String): DpRect {
        val coordinates = rule.onNodeWithTag(tag).fetchSemanticsNode().layoutInfo.coordinates
        val box = coordinates.findRootCoordinates().localBoundingBoxOf(coordinates, clipBounds = false)
        return with(rule.density) { DpRect(box.left.toDp(), box.top.toDp(), box.right.toDp(), box.bottom.toDp()) }
    }

    /**
     * Every frame the grid lies inside the board slot and moves only towards its end height, and
     * the frames while the slot is mid-way draw it strictly between the two: it follows the slot,
     * never jumps to its end size while the slot still animates.
     */
    private fun assertTheBoardFollowsTheSlot(frames: List<Pair<DpRect, DpRect>>, from: Dp, to: Dp) {
        val heights = frames.map { it.first.height }
        frames.forEachIndexed { at, (grid, slot) ->
            assertTrue("frame $at: grid $grid outside the slot $slot", grid.top >= slot.top - 0.5.dp && grid.bottom <= slot.bottom + 0.5.dp)
        }
        val towards = heights.zipWithNext().all { (a, b) -> if (to < from) b <= a + 0.5.dp else b >= a - 0.5.dp }
        assertTrue("grid heights $heights do not move steadily from $from to $to", towards)
        val slots = frames.map { it.second.height }
        // Mid-way: the slot a full dp off both its start and its end height.
        val mid = slots.indices.filter { at -> listOf(slots.first(), slots.last()).all { abs((slots[at] - it).value) > 1f } }
        assertTrue("the slot never animated: $slots", mid.isNotEmpty())
        mid.forEach { at ->
            val h = heights[at]
            assertTrue("frame $at: grid height $h not strictly between $from and $to (slot ${slots[at]})", h > minOf(from, to) && h < maxOf(from, to))
        }
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

    /**
     * The word field, which names a traced path, whole inside the board slot, its supporting line
     * (the dictionary check or the fit) too: in reach and readable, not under the tools.
     */
    private fun assertWordFieldInTheSlot() {
        val slot = slot()
        val tools = rule.onNodeWithTag(TOOL_SLOT).getUnclippedBoundsInRoot()
        // Placed: an unplaced node's bounds are unspecified, and those compare as in bounds.
        val field = rule.onNodeWithTag(WORD).assertIsDisplayed().getUnclippedBoundsInRoot()
        val line = rule.onNode(hasTestTag(WORD_CHECK) or hasTestTag(WORD_FIT), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val bottom = maxOf(field.bottom, line.bottom)
        assertTrue("word field $field outside the slot $slot", field.top >= slot.top && bottom <= slot.bottom + 0.5.dp)
        assertTrue("word field $field, its line $line, under the tools $tools", bottom <= tools.top + 0.5.dp)
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

    /**
     * The Studio over the [WORDS] board with its first [traced] rows as paths (Unique when all
     * are); animations off unless [motion].
     */
    private fun show(tested: Boolean, traced: Int = StrandsDraft.ROWS, motion: Boolean = false) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, if (motion) 1f else 0f)
        val letters = WORDS.joinToString("")
        val paths = WORDS.take(traced).mapIndexed { row, word -> StrandsDraftPath(List(StrandsDraft.COLS) { row * StrandsDraft.COLS + it }, word) }
        val draft = StrandsDraft(letters, CLUE, paths, SPANGRAM)
        val verdict = StrandsDraftAnalyzer.analyze(draft, Budget.Unlimited)
        if (traced == StrandsDraft.ROWS) assertTrue("expected Unique, got $verdict", verdict is Verdict.Unique)
        val editorDraft = EditorDraft(StrandsEditor, DraftState(draft))
        controller = StrandsEditorController(editorDraft) {}
        val passed = TestSolve(editorDraft.layoutKey, timeMs = 42_000, hints = 0).takeIf { tested }
        rule.mainClock.autoAdvance = false
        rule.setContent {
            view = LocalView.current
            PuzzleBoxTheme(darkTheme = true) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    // The app's nav shell is a SharedTransitionLayout, so the Studio is measured with a lookahead pass.
                    // The emulator's status bar above the Studio; its tab and navigation bars below.
                    SharedTransitionLayout(Modifier.fillMaxSize().padding(top = STATUS_BAR, bottom = BOTTOM_CHROME)) {
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
                    compact = true,
                    fieldCopy = StrandsEditorUi.publishFieldCopy,
                )
            },
        )
    }

    private companion object {
        const val SETTLE_MS = 2_000L

        /** Frames of the swap watched: the slot's medium tween (240 ms) is about 15 of them. */
        const val SWAP_FRAMES = 40

        /** Frames into the swap: about half the slot's tween. */
        const val MID_SWAP_FRAMES = 7
        const val LAST_CELL = StrandsDraft.ROWS * StrandsDraft.COLS - 1
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

        const val WORD_CHECK = "strands.editor.word.check"
        const val WORD_FIT = "strands.editor.word.fit"

        /** One word per row: the only tiling is the rows themselves. */
        val WORDS = listOf("SPRING", "PLANET", "HOLLOW", "BRIGHT", "FLOWER", "WINDOW", "CASTLE", "QUIVER")
    }
}
