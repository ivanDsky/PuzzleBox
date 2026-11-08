package com.ivandsky.puzzlebox.games.sudokuui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.editor.SudokuEditor
import com.ivandsky.puzzlebox.gameui.editor.EditorOverlay
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The board flags rule breaks, cells two answers disagree on, and the chosen answer's digits. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class SudokuEditorDiagnosticsTest {

    @get:Rule
    val rule = createComposeRule()

    private val editor = SudokuEditor(SudokuGames.MINI)
    private var verdict by mutableStateOf<Verdict>(Verdict.Checking)

    private fun draftOf(givens: Map<Int, Int>): EditorDraft<*> {
        val blank = EditorDraft.fresh(editor, editor.variants.first())
        val cells = List(CELLS) { givens[it] ?: 0 }
        return EditorDraft(editor, blank.state.copy(draft = blank.state.draft.copy(givens = cells)))
    }

    private fun controllerOf(givens: Map<Int, Int>) = SudokuEditorController(draftOf(givens)) {}

    private fun show(controller: SudokuEditorController, shown: Verdict) {
        verdict = shown
        rule.setContent {
            PuzzleBoxTheme { SudokuEditorBoard(controller, SudokuGames.MINI.variant.geometry, verdict = verdict) }
        }
    }

    private fun tagged(suffix: String): Set<Int> =
        (0 until CELLS).filter { rule.onAllNodesWithTag("sudoku.editor.cell.$it.$suffix").fetchSemanticsNodes().isNotEmpty() }.toSet()

    @Test
    fun `a None verdict marks exactly its cells as clashing`() {
        show(controllerOf(emptyMap()), Verdict.None(setOf(4, 5)))

        assertEquals(setOf(4, 5), tagged("clash"))
        assertEquals(emptySet<Int>(), tagged("diff"))
    }

    @Test
    fun `two equal givens in a row clash at once, before any verdict`() {
        show(controllerOf(mapOf(0 to 3, 1 to 3)), Verdict.Checking)

        assertEquals(setOf(0, 1), tagged("clash"))
    }

    @Test
    fun `a Multiple verdict shades exactly the differing cells and clashes nothing`() {
        show(controllerOf(emptyMap()), MULTIPLE)

        assertEquals(MULTIPLE.differingCells, tagged("diff"))
        assertEquals(emptySet<Int>(), tagged("clash"))
        assertEquals(emptySet<Int>(), tagged("witness"))
    }

    @Test
    fun `the A and B toggle draws each solution's own digit in a differing cell`() {
        val controller = controllerOf(emptyMap())
        controller.adoptOverlay(EditorOverlay(EditorOverlay.Witness.A, showOnBoard = true))
        show(controller, MULTIPLE)
        rule.onNodeWithTag("sudoku.editor.cell.$DIFFERING.witness").assertTextEquals(A.values[DIFFERING].toString())

        controller.adoptOverlay(EditorOverlay(EditorOverlay.Witness.B, showOnBoard = true))
        rule.waitForIdle()

        rule.onNodeWithTag("sudoku.editor.cell.$DIFFERING.witness").assertTextEquals(B.values[DIFFERING].toString())
    }

    @Test
    fun `Show on board draws the witness in every empty cell and none over a given`() {
        val controller = controllerOf(mapOf(0 to 5))
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
    fun `the overlay survives adopting a new draft`() {
        val controller = controllerOf(emptyMap())
        controller.adoptOverlay(EditorOverlay(EditorOverlay.Witness.B, showOnBoard = true))
        show(controller, MULTIPLE)

        controller.adopt(draftOf(mapOf(0 to 5)))
        rule.waitForIdle()

        assertEquals((1 until CELLS).toSet(), tagged("witness"))
        rule.onNodeWithTag("sudoku.editor.cell.$DIFFERING.witness").assertTextEquals(B.values[DIFFERING].toString())
    }

    private companion object {
        const val CELLS = 36
        const val DIFFERING = 2
        val A = Solution(List(CELLS) { it % 6 + 1 })
        val B = Solution(List(CELLS) { if (it < 4) (it + 1) % 6 + 1 else it % 6 + 1 })
        val MULTIPLE = Verdict.Multiple(A, B, (0 until 4).toSet())
    }
}
