package com.ivandsky.puzzlebox.games.queensui.editor

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.queens.QueensVariants
import com.ivandsky.puzzlebox.games.queens.editor.QueensAnswerFit
import com.ivandsky.puzzlebox.games.queens.editor.QueensCrowns
import com.ivandsky.puzzlebox.games.queens.editor.QueensDraft
import com.ivandsky.puzzlebox.games.queens.editor.QueensEditor
import com.ivandsky.puzzlebox.gameui.editor.EditorOverlay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Tickets 18–19: select cells (tap, drag, All of colour, Area), pick one ring colour → one Paint step. Ticket 36: Crown | Paint modes.
 * Ticket 31: adoptOverlay updates the overlay the board draws (witness queens without crowns, the ghosts' witness with them).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w420dp-h900dp-420dpi")
class QueensEditorControllerTest {

    @get:Rule
    val rule = createComposeRule()

    private val edits = ArrayList<EditorDraft<*>>()

    /** A 6×6 board painted by rows (row r = region r), so every cell starts painted. */
    private fun painted(): EditorDraft<QueensDraft> {
        val blank = EditorDraft.fresh(QueensEditor, QueensVariants.variant(SIZE).id)
        val rows = blank.state.draft.copy(regions = List(SIZE * SIZE) { it / SIZE })
        return EditorDraft(QueensEditor, blank.state.copy(draft = rows))
    }

    private fun controller() = QueensEditorController(painted()) { edits += it }

    private fun regionsOf(draft: EditorDraft<*>): List<Int> = (draft.state.draft as QueensDraft).regions

    private fun fresh(): EditorDraft<QueensDraft> = EditorDraft.fresh(QueensEditor, QueensVariants.variant(SIZE).id)

    private fun crownsOf(draft: EditorDraft<*>): Set<Int> = (draft.state.draft as QueensDraft).crowns

    @Test
    fun `a fresh draft opens in Crown and a painted draft in Paint`() {
        assertEquals(QueensEditorMode.Crown, QueensEditorController(fresh()) {}.mode.value)
        assertEquals(QueensEditorMode.Paint, controller().mode.value)
    }

    @Test
    fun `a crown tap is one undo step that also removes its seed`() {
        val controller = QueensEditorController(fresh()) { edits += it }

        controller.tap(7)

        assertEquals(1, edits.size)
        assertEquals(setOf(7), crownsOf(edits.single()))
        assertTrue(regionsOf(edits.single())[7] != QueensDraft.UNPAINTED)
        assertEquals(edits.single(), controller.draft.value)
        assertEquals(emptySet<Int>(), controller.selection.value)
        val undone = edits.single().undo()!!
        assertEquals(emptySet<Int>(), crownsOf(undone))
        assertEquals(regionsOf(fresh()), regionsOf(undone))
        assertTrue(undone.canUndo.not())
    }

    @Test
    fun `a crown tap on a crossed cell places it and both crowns clash`() {
        val controller = QueensEditorController(fresh()) { edits += it }
        controller.tap(7)

        controller.tap(8)

        assertEquals(2, edits.size)
        assertEquals(setOf(7, 8), crownsOf(controller.draft.value))
        assertEquals(setOf(7, 8), QueensCrowns.conflicts(controller.draft.value.state.draft as QueensDraft))
    }

    @Test
    fun `removing one of two clashing crowns clears the clash`() {
        val controller = QueensEditorController(fresh()) { edits += it }
        controller.tap(7)
        controller.tap(8)

        controller.tap(8)

        assertEquals(setOf(7), crownsOf(controller.draft.value))
        assertEquals(emptySet<Int>(), QueensCrowns.conflicts(controller.draft.value.state.draft as QueensDraft))
    }

    @Test
    fun `a crown tap on a crown removes it`() {
        val controller = QueensEditorController(fresh()) { edits += it }
        controller.tap(7)

        controller.tap(7)

        assertEquals(2, edits.size)
        assertEquals(emptySet<Int>(), crownsOf(edits.last()))
        assertEquals(edits.last(), controller.draft.value)
    }

    @Test
    fun `in Paint a tap selects as before and never edits crowns`() {
        val controller = QueensEditorController(fresh()) { edits += it }
        controller.tap(7)

        controller.setMode(QueensEditorMode.Paint)
        controller.tap(7)
        controller.tap(8)

        assertEquals(QueensEditorMode.Paint, controller.mode.value)
        assertEquals(setOf(7, 8), controller.selection.value)
        assertEquals(1, edits.size)
        assertEquals(setOf(7), crownsOf(controller.draft.value))
    }

    /** [painted] with crowns on [crowns]; row r is region r, so each crown's region is its row. */
    private fun crowned(vararg crowns: Int): QueensEditorController {
        val base = painted()
        val state = base.state.copy(draft = base.state.draft.copy(crowns = crowns.toSet()))
        return QueensEditorController(EditorDraft(QueensEditor, state)) { edits += it }
    }

    @Test
    fun `tapping the queen last accumulates without filling until settle, then one undo step`() {
        val controller = crowned(1)

        listOf(7, 14, 20, 1).forEach(controller::tap)
        assertEquals(setOf(7, 14, 20, 1), controller.selection.value)
        assertEquals(0, edits.size)
        controller.settle()

        assertEquals(1, edits.size)
        listOf(1, 7, 14, 20).forEach { assertEquals(0, regionsOf(edits.single())[it]) }
        assertEquals(emptySet<Int>(), controller.selection.value)
        assertEquals(regionsOf(painted()), regionsOf(edits.single().undo()!!))
    }

    @Test
    fun `tapping the queen first accumulates without filling until settle, then one undo step`() {
        val controller = crowned(1)

        listOf(1, 7, 14, 20).forEach(controller::tap)
        assertEquals(setOf(1, 7, 14, 20), controller.selection.value)
        assertEquals(0, edits.size)
        controller.settle()

        assertEquals(1, edits.size)
        listOf(1, 7, 14, 20).forEach { assertEquals(0, regionsOf(edits.single())[it]) }
        assertEquals(regionsOf(painted()), regionsOf(edits.single().undo()!!))
    }

    @Test
    fun `an area holding one crown paints with its region as one undo step`() {
        val controller = crowned(14)
        controller.tap(7)

        controller.selectArea(7, 14)

        assertEquals(1, edits.size)
        assertEquals(2, regionsOf(edits.single())[7])
        assertEquals(emptySet<Int>(), controller.selection.value)
        assertEquals(regionsOf(painted()), regionsOf(edits.single().undo()!!))
    }

    @Test
    fun `a selection with two crowns or none is not settled and makes no edit`() {
        val two = crowned(1, 14)
        listOf(1, 14, 7).forEach(two::tap)
        val none = controller()
        listOf(7, 1, 14).forEach(none::tap)

        two.settle()
        none.settle()

        assertEquals(setOf(7, 1, 14), two.selection.value)
        assertEquals(setOf(7, 1, 14), none.selection.value)
        assertEquals(0, edits.size)
    }

    @Test
    fun `a crown alone, or with cells already its colour, has nothing to fill and keeps the selection`() {
        val controller = crowned(1)

        controller.tap(1)
        controller.tap(2)
        controller.settle()

        assertEquals(setOf(1, 2), controller.selection.value)
        assertEquals(0, edits.size)
    }

    @Test
    fun `an unpainted crown cannot lend a colour`() {
        val base = fresh()
        val state = base.state.copy(draft = base.state.draft.copy(crowns = setOf(1)))
        val controller = QueensEditorController(EditorDraft(QueensEditor, state)) { edits += it }
        controller.setMode(QueensEditorMode.Paint)

        controller.tap(7)
        controller.tap(1)
        controller.settle()

        assertEquals(setOf(7, 1), controller.selection.value)
        assertEquals(0, edits.size)
    }

    @Test
    fun `switching mode clears the selection without an edit`() {
        val controller = controller()
        listOf(0, 7).forEach(controller::tap)

        controller.setMode(QueensEditorMode.Crown)

        assertEquals(QueensEditorMode.Crown, controller.mode.value)
        assertEquals(emptySet<Int>(), controller.selection.value)
        assertEquals(0, edits.size)
    }

    @Test
    fun `tapping the mode already on keeps the selection`() {
        val controller = controller()
        listOf(0, 7).forEach(controller::tap)

        controller.setMode(QueensEditorMode.Paint)

        assertEquals(setOf(0, 7), controller.selection.value)
    }

    @Test
    fun `select replaces the selection with exactly its cells in Paint, in one emission`() = runTest {
        val controller = QueensEditorController(fresh()) { edits += it }
        controller.setMode(QueensEditorMode.Paint)
        controller.tap(3)
        controller.setMode(QueensEditorMode.Crown)
        val seen = ArrayList<Set<Int>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { controller.selection.collect { seen += it } }

        controller.select(linkedSetOf(9, 2, 5))

        assertEquals(QueensEditorMode.Paint, controller.mode.value)
        assertEquals(listOf(9, 2, 5), controller.selection.value.toList())
        assertEquals(listOf(emptySet(), setOf(9, 2, 5)), seen)
        assertEquals(0, edits.size)
    }

    @Test
    fun `extend adds the crossed cells and keeps the selected ones`() {
        val controller = controller()
        controller.tap(4)

        controller.extend(listOf(4, 5, 6))

        assertEquals(listOf(4, 5, 6), controller.selection.value.toList())
        assertEquals(0, edits.size)
    }

    @Test
    fun `three selected cells and one pick give exactly one Paint step and an empty selection`() {
        val controller = controller()
        listOf(0, 7, 14).forEach(controller::tap)
        assertEquals(setOf(0, 7, 14), controller.selection.value)

        controller.pick(4)

        assertEquals(1, edits.size)
        val regions = regionsOf(edits.single())
        listOf(0, 7, 14).forEach { assertEquals(4, regions[it]) }
        assertEquals(regionsOf(painted()).filterIndexed { i, _ -> i !in setOf(0, 7, 14) },
            regions.filterIndexed { i, _ -> i !in setOf(0, 7, 14) })
        assertEquals(emptySet<Int>(), controller.selection.value)
        assertEquals(edits.single(), controller.draft.value)
        assertEquals(regionsOf(painted()), regionsOf(edits.single().undo()!!))
        assertTrue(edits.single().undo()!!.canUndo.not())
    }

    @Test
    fun `a tap toggles a cell and cancel clears the selection without an edit`() {
        val controller = controller()
        controller.tap(3)
        controller.tap(5)
        controller.tap(3)
        assertEquals(setOf(5), controller.selection.value)

        controller.cancel()

        assertEquals(emptySet<Int>(), controller.selection.value)
        assertEquals(0, edits.size)
    }

    @Test
    fun `a pick with nothing selected is not an edit`() {
        val controller = controller()
        controller.pick(2)
        assertEquals(0, edits.size)
    }

    @Test
    fun `a tap on a cell opens the ring at once`() {
        val controller = controller()
        show(controller)
        rule.onAllNodesWithTag(RING).assertCountEquals(0)

        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("queens.editor.cell.0", useUnmergedTree = true).performClick()
        rule.mainClock.advanceTimeByFrame()

        rule.onAllNodesWithTag(RING).assertCountEquals(1)
        assertEquals(setOf(0), controller.selection.value)
        assertEquals(0, edits.size)
    }

    @Test
    fun `picking the ring's slot paints the selection in one step`() {
        val controller = controller()
        show(controller)
        listOf(0, 7, 14).forEach(controller::tap)

        rule.onNodeWithTag("queens.editor.ring.slot.4").performClick()

        assertEquals(1, edits.size)
        listOf(0, 7, 14).forEach { assertEquals(4, regionsOf(edits.single())[it]) }
        rule.onAllNodesWithTag(RING).assertCountEquals(0)
    }

    @Test
    fun `picking the ring's unpaint centre unpaints the selection`() {
        val controller = controller()
        show(controller)
        listOf(0, 7, 14).forEach(controller::tap)

        rule.onNodeWithTag(UNPAINT).performClick()

        assertEquals(1, edits.size)
        listOf(0, 7, 14).forEach {
            assertEquals(QueensDraft.UNPAINTED, regionsOf(edits.single())[it])
            rule.onNodeWithTag("queens.editor.cell.$it", useUnmergedTree = true)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, UNPAINTED_DESCRIPTION))
        }
        assertEquals(emptySet<Int>(), controller.selection.value)
    }

    @Test
    fun `selectAllOf grows the selection to exactly that region's cells without an edit`() {
        val controller = controller()
        controller.tap(20)

        controller.selectAllOf(3)

        assertEquals((3 * SIZE until 4 * SIZE).toSet(), controller.selection.value)
        assertEquals(0, edits.size)
    }

    @Test
    fun `selectArea selects the bounding rectangle from the first cell to the last`() {
        val controller = controller()

        controller.selectArea(0, 2 * SIZE + 2)

        assertEquals(setOf(0, 1, 2, 6, 7, 8, 12, 13, 14), controller.selection.value)
        controller.cancel()
        controller.selectArea(2 * SIZE + 2, 0)
        assertEquals(setOf(0, 1, 2, 6, 7, 8, 12, 13, 14), controller.selection.value)
        assertEquals(0, edits.size)
    }

    @Test
    fun `the context bar counts the selection and its chips grow or cancel it`() {
        val controller = controller()
        show(controller)
        rule.onAllNodesWithTag(COUNT).assertCountEquals(0)
        rule.onNodeWithTag(HINT).assertExists()

        controller.tap(0)
        controller.tap(2 * SIZE + 2)
        rule.onNodeWithTag(COUNT).assert(count(2))

        rule.onNodeWithTag(AREA).performClick()
        assertEquals(setOf(0, 1, 2, 6, 7, 8, 12, 13, 14), controller.selection.value)
        rule.onNodeWithTag(COUNT).assert(count(9))
        rule.onAllNodesWithTag(RING).assertCountEquals(1)

        rule.onNodeWithTag(ALL_OF).performClick()
        assertEquals((0 until 3 * SIZE).toSet(), controller.selection.value)
        rule.onNodeWithTag(COUNT).assert(count(3 * SIZE))

        rule.onNodeWithTag(CANCEL).performClick()
        assertEquals(emptySet<Int>(), controller.selection.value)
        rule.onAllNodesWithTag(COUNT).assertCountEquals(0)
        rule.onAllNodesWithTag(RING).assertCountEquals(0)
        assertEquals(0, edits.size)
    }

    @Test
    fun `a stroke paints every crossed non-crown cell its start's region in one undo step`() {
        val start = painted()
        val crowned = EditorDraft(QueensEditor, start.state.copy(draft = start.state.draft.copy(crowns = setOf(CROWNED))))
        val controller = QueensEditorController(crowned) { edits += it }

        controller.stroke(0, listOf(0, 1, 2, CROWNED, 14))

        assertEquals(1, edits.size)
        val regions = regionsOf(edits.single())
        listOf(0, 1, 2, 14).forEach { assertEquals(0, regions[it]) }
        assertEquals(1, regions[CROWNED])
        assertEquals(setOf(CROWNED), crownsOf(edits.single()))
        assertEquals(edits.single(), controller.draft.value)
        val undone = edits.single().undo()!!
        assertEquals(regionsOf(painted()), regionsOf(undone))
        assertTrue(undone.canUndo.not())
    }

    @Test
    fun `a stroke from an unpainted cell changes nothing`() {
        val controller = QueensEditorController(fresh()) { edits += it }

        controller.stroke(0, listOf(0, 1, 2))

        assertEquals(0, edits.size)
        assertEquals(regionsOf(fresh()), regionsOf(controller.draft.value))
    }

    @Test
    fun `adoptOverlay B with Show on board draws exactly B's queens as the witness on a crownless board`() {
        val controller = controller()
        controller.adoptOverlay(EditorOverlay(EditorOverlay.Witness.B, showOnBoard = true))
        assertEquals(EditorOverlay.Witness.B, controller.overlay.value.witness)

        showVerdict(controller, MULTIPLE)

        assertEquals(MULTIPLE.solutionB.values.toSet(), tagged("witness"))
        assertEquals(emptySet<Int>(), tagged("ghost"))
    }

    @Test
    fun `a crownless Multiple opens at the default overlay, so the diff ring stays and no witness is drawn`() {
        val controller = controller()
        val opening = controller.openingOverlay(MULTIPLE)
        assertEquals(EditorOverlay(), opening)
        controller.adoptOverlay(opening)

        showVerdict(controller, MULTIPLE)

        assertEquals(emptySet<Int>(), tagged("witness"))
        assertEquals(MULTIPLE.differingCells, tagged("diff"))
    }

    @Test
    fun `with crowns on B's queens the card opens on A with Show on board, and flipping to B redraws the ghosts`() {
        val start = painted()
        val board = start.state.draft.copy(crowns = CROWNS_ON_B)
        val controller = QueensEditorController(EditorDraft(QueensEditor, start.state.copy(draft = board))) {}
        val opening = controller.openingOverlay(MULTIPLE)
        assertEquals(EditorOverlay(EditorOverlay.Witness.A, showOnBoard = true), opening)
        controller.adoptOverlay(opening)

        showVerdict(controller, MULTIPLE)
        assertEquals(QueensAnswerFit.ghostsOf(board, MULTIPLE, b = false), tagged("ghost"))
        assertEquals(emptySet<Int>(), tagged("witness"))

        controller.adoptOverlay(opening.copy(witness = EditorOverlay.Witness.B))
        rule.waitForIdle()
        assertEquals(QueensAnswerFit.ghostsOf(board, MULTIPLE, b = true), tagged("ghost"))
        assertEquals(emptySet<Int>(), tagged("witness"))
    }

    private fun showVerdict(controller: QueensEditorController, verdict: Verdict) {
        rule.setContent {
            PuzzleBoxTheme { QueensEditorBoard(controller, verdict, Modifier.fillMaxWidth()) }
        }
    }

    /** Every cell carrying `queens.editor.cell.<idx>.<mark>`. */
    private fun tagged(mark: String): Set<Int> =
        (0 until SIZE * SIZE).filterTo(sortedSetOf()) { idx ->
            rule.onAllNodesWithTag("queens.editor.cell.$idx.$mark", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        }

    private fun count(k: Int) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "$k")

    private fun show(controller: QueensEditorController) {
        rule.setContent {
            PuzzleBoxTheme { QueensEditorBoard(controller, Verdict.Checking, Modifier.fillMaxWidth()) }
        }
    }

    private companion object {
        const val SIZE = 6
        const val CROWNED = 8 // row 1 (region 1), inside the stroke's path

        /** Two answers on the row-painted board, sharing no cell. */
        val MULTIPLE = Verdict.Multiple(
            Solution(listOf(0, 8, 16, 24, 32, 34)),
            Solution(listOf(1, 9, 14, 22, 30, 35)),
            setOf(0, 8, 16, 24, 32, 34, 1, 9, 14, 22, 30, 35),
        )

        /** Two of B's queens crowned, none of A's: the author's witness is B. */
        val CROWNS_ON_B = setOf(1, 9)
        const val RING = "queens.editor.ring"
        const val UNPAINT = "queens.editor.ring.unpaint"
        const val COUNT = "queens.editor.context.count"
        const val HINT = "queens.editor.context.hint"
        const val AREA = "queens.editor.chip.area"
        const val ALL_OF = "queens.editor.chip.allOf"
        const val CANCEL = "queens.editor.chip.cancel"
    }
}
