package com.ivandsky.puzzlebox.games.strandsui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.strands.StrandsDefinition
import com.ivandsky.puzzlebox.games.strands.editor.StrandsEditor
import com.ivandsky.puzzlebox.gameui.editor.EditorOverlay
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The board flags uncoverable cells, cells two tilings disagree on, and the chosen tiling. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class StrandsEditorDiagnosticsTest {

    @get:Rule
    val rule = createComposeRule()

    private val controller = StrandsEditorController(EditorDraft.fresh(StrandsEditor, StrandsDefinition.VARIANT)) {}
    private var verdict by mutableStateOf<Verdict>(Verdict.Checking)

    private fun show(shown: Verdict) {
        verdict = shown
        rule.setContent {
            PuzzleBoxTheme { StrandsEditorUi.Board(controller, verdict, Modifier) }
        }
    }

    private fun tagged(suffix: String): Set<Int> =
        (0 until CELLS).filter { rule.onAllNodesWithTag("strands.editor.cell.$it.$suffix").fetchSemanticsNodes().isNotEmpty() }.toSet()

    private fun wordAt(cell: Int): String? =
        rule.onNodeWithTag("strands.editor.cell.$cell.witness")
            .fetchSemanticsNode().config[SemanticsProperties.StateDescription]

    @Test
    fun `a None verdict marks exactly its cells as clashing`() {
        show(Verdict.None(setOf(4, 5)))

        assertEquals(setOf(4, 5), tagged("clash"))
        assertEquals(emptySet<Int>(), tagged("diff"))
    }

    @Test
    fun `a Multiple verdict shades exactly the differing cells and clashes nothing`() {
        show(MULTIPLE)

        assertEquals(MULTIPLE.differingCells, tagged("diff"))
        assertEquals(emptySet<Int>(), tagged("clash"))
        assertEquals(emptySet<Int>(), tagged("witness"))
    }

    @Test
    fun `Show on board draws the chosen tiling on every cell`() {
        controller.adoptOverlay(EditorOverlay(showOnBoard = true))
        show(MULTIPLE)

        assertEquals((0 until CELLS).toSet(), tagged("witness"))
    }

    @Test
    fun `the A and B toggle switches the witness tiling`() {
        controller.adoptOverlay(EditorOverlay(EditorOverlay.Witness.A, showOnBoard = true))
        show(MULTIPLE)
        val first = wordAt(DIFFERING)

        controller.adoptOverlay(EditorOverlay(EditorOverlay.Witness.B, showOnBoard = true))
        rule.waitForIdle()

        assertEquals(A.values[DIFFERING].toString(), first)
        assertEquals(B.values[DIFFERING].toString(), wordAt(DIFFERING))
    }

    @Test
    fun `the witness stays through Checking and goes when Show on board is off`() {
        controller.adoptOverlay(EditorOverlay(showOnBoard = true))
        show(MULTIPLE)
        verdict = Verdict.Checking
        rule.waitForIdle()
        assertEquals((0 until CELLS).toSet(), tagged("witness"))
        assertEquals(emptySet<Int>(), tagged("diff"))

        controller.adoptOverlay(EditorOverlay(showOnBoard = false))
        rule.waitForIdle()
        assertEquals(emptySet<Int>(), tagged("witness"))
    }

    @Test
    fun `the overlay is held by the controller`() {
        controller.adoptOverlay(EditorOverlay(EditorOverlay.Witness.B, showOnBoard = true))

        assertEquals(EditorOverlay(EditorOverlay.Witness.B, showOnBoard = true), controller.overlay.value)
    }

    private companion object {
        const val CELLS = 48
        const val DIFFERING = 2
        val A = Solution(List(CELLS) { it / 4 })
        val B = Solution(List(CELLS) { if (it < 4) 12 else it / 4 })
        val MULTIPLE = Verdict.Multiple(A, B, (0 until 4).toSet())
    }
}
