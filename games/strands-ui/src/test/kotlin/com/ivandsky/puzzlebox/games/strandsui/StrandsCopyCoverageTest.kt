package com.ivandsky.puzzlebox.games.strandsui

import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.IncompleteReason
import com.ivandsky.puzzlebox.game.editor.PublishBlock
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.strands.StrandsDefinition
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraftAnalyzer
import com.ivandsky.puzzlebox.games.strands.editor.StrandsEditor
import com.ivandsky.puzzlebox.games.strands.editor.StrandsPublishGates
import com.ivandsky.puzzlebox.games.strandsui.editor.StrandsEditorController
import com.ivandsky.puzzlebox.games.strandsui.editor.StrandsEditorUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every id the Strands editor can hand the fix list has copy: a reason
 * ([StrandsDraftAnalyzer.reasonIds]) or block ([StrandsPublishGates.blockIds]) without a row
 * fails here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StrandsCopyCoverageTest {

    @get:Rule
    val rule = createComposeRule()

    private val controller = StrandsEditorController(EditorDraft.fresh(StrandsEditor, StrandsDefinition.VARIANT)) {}
    private var selects = 0

    private fun show(verdict: Verdict, blocks: List<PublishBlock>) = rule.setContent {
        PuzzleBoxTheme { StrandsEditorUi.Issues(controller, verdict, blocks, { selects++ }, Modifier) }
    }

    private fun rowText(tag: String): String =
        rule.onAllNodesWithTag(tag).fetchSemanticsNodes()
            .flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty() }
            .joinToString { it.text }

    @Test
    fun `every reason renders a non-empty fix row`() {
        val ids = StrandsDraftAnalyzer.reasonIds.toList()
        var shown by mutableStateOf(ids.first())
        rule.setContent {
            PuzzleBoxTheme {
                StrandsEditorUi.Issues(controller, Verdict.Incomplete(IncompleteReason(shown, 2)), emptyList(), {}, Modifier)
            }
        }

        val missing = ids.filter { id ->
            shown = id
            rule.waitForIdle()
            rowText("strands.editor.fix.$id").isBlank()
        }

        assertEquals(emptyList<String>(), missing)
    }

    @Test
    fun `every block renders a non-empty fix row`() {
        val ids = StrandsPublishGates.blockIds
        show(Verdict.Checking, ids.map { PublishBlock(it, subject = 0, count = 1) })

        val missing = ids.filter { id -> rowText("strands.editor.fix.$id").isBlank() }

        assertEquals(emptyList<String>(), missing)
    }

    @Test
    fun `an id without copy draws no row`() {
        show(Verdict.Incomplete(IncompleteReason("strands.unknown", 1)), listOf(PublishBlock(PublishBlock.NOT_UNIQUE)))

        assertTrue(rule.onAllNodesWithTag("strands.editor.fix.strands.unknown").fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodesWithTag("strands.editor.fix.${PublishBlock.NOT_UNIQUE}").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun `selecting a word block selects its path and closes the card`() {
        listOf(0, 1, 2).forEach(controller::traceTo)
        controller.endTrace()
        show(Verdict.Checking, listOf(PublishBlock(StrandsPublishGates.WORD_SHAPE, subject = 0)))

        rule.onNodeWithTag("strands.editor.fix.${StrandsPublishGates.WORD_SHAPE}.select").performClick()

        assertEquals(0, controller.selectedPath.value)
        assertEquals(1, selects)
    }
}
