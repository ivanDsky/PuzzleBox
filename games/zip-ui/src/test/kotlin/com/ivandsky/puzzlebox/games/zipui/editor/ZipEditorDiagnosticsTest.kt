package com.ivandsky.puzzlebox.games.zipui.editor

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.zip.editor.ZipEditor
import com.ivandsky.puzzlebox.games.zip.engine.ZipVariants
import com.ivandsky.puzzlebox.gameui.editor.EditorOverlay
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The board flags cells two paths disagree on, the cells to blame, and the chosen witness path. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w420dp-h900dp-420dpi")
class ZipEditorDiagnosticsTest {

    @get:Rule
    val rule = createComposeRule()

    private var verdict by mutableStateOf<Verdict>(Verdict.Checking)

    private fun controller(): ZipEditorController {
        val blank = EditorDraft.fresh(ZipEditor, ZipEditor.variants.first())
        return ZipEditorController(blank) {}
    }

    private fun show(controller: ZipEditorController, shown: Verdict) {
        verdict = shown
        rule.setContent {
            PuzzleBoxTheme { ZipEditorBoard(controller, Modifier.fillMaxSize(), verdict) }
        }
    }

    private fun tagged(suffix: String): Set<Int> = (0 until CELLS).filter {
        rule.onAllNodesWithTag("zip.editor.cell.$it.$suffix").fetchSemanticsNodes().isNotEmpty()
    }.toSet()

    @Test
    fun `a None verdict marks exactly the blamed cells`() {
        show(controller(), Verdict.None(setOf(3, 9)))

        assertEquals(setOf(3, 9), tagged("clash"))
        assertEquals(emptySet<Int>(), tagged("diff"))
        assertEquals(emptySet<Int>(), tagged("witness"))
    }

    @Test
    fun `a Multiple verdict rings exactly the differing cells and blames nothing`() {
        show(controller(), MULTIPLE)

        assertEquals(MULTIPLE.differingCells, tagged("diff"))
        assertEquals(emptySet<Int>(), tagged("clash"))
        assertEquals(emptySet<Int>(), tagged("witness"))
    }

    @Test
    fun `Show on board draws witness A and the toggle switches it to B`() {
        val controller = controller()
        controller.adoptOverlay(EditorOverlay(EditorOverlay.Witness.A, showOnBoard = true))
        show(controller, MULTIPLE)
        assertEquals(A.values.toSet(), tagged("witness"))

        controller.adoptOverlay(EditorOverlay(EditorOverlay.Witness.B, showOnBoard = true))
        rule.waitForIdle()
        assertEquals(B.values.toSet(), tagged("witness"))
    }

    @Test
    fun `the witness stays through Checking and goes when Show on board is off`() {
        val controller = controller()
        controller.adoptOverlay(EditorOverlay(showOnBoard = true))
        show(controller, MULTIPLE)
        verdict = Verdict.Checking
        rule.waitForIdle()
        assertEquals(A.values.toSet(), tagged("witness"))
        assertEquals(emptySet<Int>(), tagged("diff"))

        controller.adoptOverlay(EditorOverlay(showOnBoard = false))
        rule.waitForIdle()
        assertEquals(emptySet<Int>(), tagged("witness"))
    }

    @Test
    fun `the witness choice survives a replacement draft`() {
        val controller = controller()
        controller.adoptOverlay(EditorOverlay(EditorOverlay.Witness.B, showOnBoard = true))
        show(controller, MULTIPLE)

        controller.adopt(EditorDraft.fresh(ZipEditor, ZipEditor.variants.first()))
        rule.waitForIdle()

        assertEquals(B.values.toSet(), tagged("witness"))
    }

    private companion object {
        val CELLS = ZipVariants.byId(ZipEditor.variants.first())!!.size.let { it * it }
        val A = Solution(listOf(0, 1, 2, 3, 4))
        val B = Solution(listOf(0, 5, 6, 7, 8))
        val MULTIPLE = Verdict.Multiple(A, B, setOf(1, 2, 3, 4, 5, 6, 7, 8))
    }
}
