package com.ivandsky.puzzlebox.games.zipui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.IncompleteReason
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.zip.editor.ZipDraftAnalyzer
import com.ivandsky.puzzlebox.games.zip.editor.ZipEditor
import com.ivandsky.puzzlebox.games.zipui.editor.ZipEditorController
import com.ivandsky.puzzlebox.games.zipui.editor.ZipEditorCopy
import com.ivandsky.puzzlebox.games.zipui.editor.ZipEditorUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Every id the Zip engine can hand the UI has fix-list copy, and the verdict card shows it. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w420dp-h900dp-420dpi")
class ZipCopyCoverageTest {

    @get:Rule
    val rule = createComposeRule()

    private var verdict by mutableStateOf<Verdict>(Verdict.Undetermined)

    private val controller =
        ZipEditorController(EditorDraft.fresh(ZipEditor, ZipEditor.variants.first())) {}

    @Test
    fun `every reason id has copy`() {
        val ids = ZipDraftAnalyzer.reasonIds
        assertTrue("the engine lists its ids", ids.isNotEmpty())

        val missing = ids.filter { ZipEditorCopy.fix(it).isNullOrBlank() }

        assertEquals(emptyList<String>(), missing)
    }

    @Test
    fun `an unknown id has no copy`() {
        assertNull(ZipEditorCopy.fix("zip.unknown"))
    }

    @Test
    fun `an Incomplete verdict lists its reason as a fix row`() {
        showIssues()
        for (id in ZipDraftAnalyzer.reasonIds) {
            verdict = Verdict.Incomplete(IncompleteReason(id, 1))
            rule.waitForIdle()

            rule.onAllNodesWithTag("zip.editor.fix.$id").assertCountEquals(1)
        }
    }

    @Test
    fun `a verdict without a reason draws no fix rows`() {
        showIssues()

        rule.onAllNodesWithTag("zip.editor.fix").assertCountEquals(0)
    }

    private fun showIssues() {
        rule.setContent {
            PuzzleBoxTheme {
                ZipEditorUi.Issues(
                    controller,
                    verdict,
                    emptyList(),
                    onSelect = {},
                    modifier = Modifier,
                )
            }
        }
    }
}
