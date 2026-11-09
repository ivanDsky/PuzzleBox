package com.ivandsky.puzzlebox.games.tangoui.editor

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.editor.TangoDraft
import com.ivandsky.puzzlebox.games.tango.editor.TangoEditor
import com.ivandsky.puzzlebox.gameui.editor.EditorOverlay
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The board flags rule breaks, cells two answers disagree on, and the chosen answer's symbols. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w420dp-h900dp-420dpi")
class TangoEditorDiagnosticsTest {

    @get:Rule
    val rule = createComposeRule()

    private var verdict by mutableStateOf<Verdict>(Verdict.Checking)

    private fun controllerOf(givens: Map<Int, TangoCell>): TangoEditorController {
        val blank = EditorDraft.fresh(TangoEditor, TangoEditor.variants.first())
        val draft = EditorDraft(TangoEditor, blank.state.copy(draft = blank.state.draft.copy(givens = givens)))
        return TangoEditorController(draft) {}
    }

    private fun show(controller: TangoEditorController, shown: Verdict) {
        verdict = shown
        rule.setContent {
            PuzzleBoxTheme { TangoEditorBoard(controller, Modifier.fillMaxWidth(), verdict) }
        }
    }

    private fun tagged(suffix: String, cells: Int = CELLS): Set<Int> =
        (0 until cells).filter { rule.onAllNodesWithTag("tango.editor.cell.$it.$suffix").fetchSemanticsNodes().isNotEmpty() }.toSet()

    @Test
    fun `a None verdict marks exactly the clash cells`() {
        show(controllerOf(emptyMap()), Verdict.None(setOf(4, 5)))

        assertEquals(setOf(4, 5), tagged("clash"))
        assertEquals(emptySet<Int>(), tagged("diff"))
    }

    @Test
    fun `three suns in a row clash at once, before any verdict`() {
        show(controllerOf(mapOf(0 to TangoCell.SUN, 1 to TangoCell.SUN, 2 to TangoCell.SUN)), Verdict.Checking)

        assertEquals(setOf(0, 1, 2), tagged("clash"))
    }

    @Test
    fun `a Multiple verdict shades exactly the differing cells and clashes nothing`() {
        show(controllerOf(emptyMap()), MULTIPLE)

        assertEquals(MULTIPLE.differingCells, tagged("diff"))
        assertEquals(emptySet<Int>(), tagged("clash"))
        assertEquals(emptySet<Int>(), tagged("witness"))
    }

    @Test
    fun `Show on board draws the witness in every empty cell and none over a given`() {
        val controller = controllerOf(mapOf(0 to TangoCell.SUN))
        controller.adoptOverlay(EditorOverlay(EditorOverlay.Witness.B, showOnBoard = true))
        show(controller, MULTIPLE)

        assertEquals((1 until CELLS).toSet(), tagged("witness"))
    }

    @Test
    fun `the witness stays through Checking and goes when Show on board is off`() {
        val controller = controllerOf(emptyMap())
        controller.adoptOverlay(EditorOverlay(showOnBoard = true))
        show(controller, MULTIPLE)
        verdict = Verdict.Checking
        rule.waitForIdle()
        assertEquals((0 until CELLS).toSet(), tagged("witness"))
        assertEquals(emptySet<Int>(), tagged("diff"))

        controller.adoptOverlay(EditorOverlay(showOnBoard = false))
        rule.waitForIdle()
        assertEquals(emptySet<Int>(), tagged("witness"))
    }

    @Test
    fun `on a 10x10 board clash rings land on the clashing cells`() {
        val board = TangoDraft(10, (97..99).associateWith { TangoCell.SUN }, emptyList())
        show(TangoEditorController(EditorDraft(TangoEditor, DraftState(board))) {}, Verdict.None(setOf(55)))

        assertEquals(setOf(55, 97, 98, 99), tagged("clash", cells = 100))
        val bounds = rule.onNodeWithTag("tango.editor.board").getUnclippedBoundsInRoot()
        val pitch = (bounds.right - bounds.left).value / 10
        listOf(55, 99).forEach { idx ->
            val cell = rule.onNodeWithTag("tango.editor.cell.$idx.clash").getUnclippedBoundsInRoot()
            assertEquals(bounds.left.value + pitch * (idx % 10), cell.left.value, 0.5f)
            assertEquals(bounds.top.value + pitch * (idx / 10), cell.top.value, 0.5f)
            assertEquals(pitch, (cell.right - cell.left).value, 0.5f)
        }
    }

    @Test
    fun `on a 4x4 board the differ shade and the witness land on their cells`() {
        assertDiffAndWitnessAt(4)
    }

    @Test
    fun `on an 8x8 board the differ shade and the witness land on their cells`() {
        assertDiffAndWitnessAt(8)
    }

    @Test
    fun `on a 10x10 board the differ shade and the witness land on their cells`() {
        assertDiffAndWitnessAt(10)
    }

    /** A Multiple verdict on an n×n board with a given in cell 0: shade on the differing cells, witness in every other cell. */
    private fun assertDiffAndWitnessAt(n: Int) {
        val cells = n * n
        val last = cells - 1
        val differing = setOf(1, n - 1, cells - n, last)
        val shown = Verdict.Multiple(Solution(List(cells) { it % 2 }), Solution(List(cells) { (it + 1) % 2 }), differing)
        val board = TangoDraft(n, mapOf(0 to TangoCell.SUN), emptyList())
        val controller = TangoEditorController(EditorDraft(TangoEditor, DraftState(board))) {}
        controller.adoptOverlay(EditorOverlay(showOnBoard = true))
        show(controller, shown)

        assertEquals(differing, tagged("diff", cells = cells))
        assertEquals((1 until cells).toSet(), tagged("witness", cells = cells))
        val bounds = rule.onNodeWithTag("tango.editor.board").getUnclippedBoundsInRoot()
        val pitch = (bounds.right - bounds.left).value / n
        listOf("diff" to n - 1, "witness" to last).forEach { (suffix, idx) ->
            val cell = rule.onNodeWithTag("tango.editor.cell.$idx.$suffix").getUnclippedBoundsInRoot()
            assertEquals("$suffix $idx left", bounds.left.value + pitch * (idx % n), cell.left.value, 0.5f)
            assertEquals("$suffix $idx top", bounds.top.value + pitch * (idx / n), cell.top.value, 0.5f)
            assertEquals("$suffix $idx width", pitch, (cell.right - cell.left).value, 0.5f)
        }
    }

    private companion object {
        const val CELLS = 6 * 6
        val A = Solution(List(CELLS) { it % 2 })
        val B = Solution(List(CELLS) { (it + if (it < 4) 1 else 0) % 2 })
        val MULTIPLE = Verdict.Multiple(A, B, setOf(0, 1, 2, 3))
    }
}
