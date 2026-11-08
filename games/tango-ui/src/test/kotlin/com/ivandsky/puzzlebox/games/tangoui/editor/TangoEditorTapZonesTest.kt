package com.ivandsky.puzzlebox.games.tangoui.editor

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.IncompleteReason
import com.ivandsky.puzzlebox.game.editor.PublishBlock
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.tango.editor.TangoDraftAnalyzer
import com.ivandsky.puzzlebox.games.tango.editor.TangoEditor
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The tap-zones toggle outlines the edge bands; the fix list names each reason and block. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w420dp-h900dp-420dpi")
class TangoEditorTapZonesTest {

    @get:Rule
    val rule = createComposeRule()

    private val controller =
        TangoEditorController(EditorDraft.fresh(TangoEditor, TangoEditor.variants.first())) {}

    private fun count(tag: String) = rule.onAllNodesWithTag(tag).fetchSemanticsNodes().size

    @Test
    fun `tap zones start off and toggle on and off`() {
        assertFalse(controller.tapZones.value)

        controller.toggleTapZones()
        assertTrue(controller.tapZones.value)

        controller.toggleTapZones()
        assertFalse(controller.tapZones.value)
    }

    @Test
    fun `the board outlines the bands only while tap zones are on`() {
        rule.setContent { PuzzleBoxTheme { TangoEditorBoard(controller, Modifier.fillMaxWidth()) } }
        rule.onAllNodesWithTag("tango.editor.tapZones.bands").assertCountEquals(0)

        controller.toggleTapZones()
        rule.waitForIdle()
        rule.onAllNodesWithTag("tango.editor.tapZones.bands").assertCountEquals(1)

        controller.toggleTapZones()
        rule.waitForIdle()
        rule.onAllNodesWithTag("tango.editor.tapZones.bands").assertCountEquals(0)
    }

    @Test
    fun `the tool hint's toggle flips the controller`() {
        rule.setContent { PuzzleBoxTheme { TangoEditorUi.ToolHint(controller, Modifier) } }

        rule.onNodeWithTag("tango.editor.tapZones").performClick()
        assertTrue(controller.tapZones.value)

        rule.onNodeWithTag("tango.editor.tapZones").performClick()
        assertFalse(controller.tapZones.value)
    }

    @Test
    fun `the tool hint carries the press-preview-lift line`() {
        rule.setContent { PuzzleBoxTheme { TangoEditorUi.ToolHint(controller, Modifier) } }

        assertEquals1("tango.editor.hint")
    }

    @Test
    fun `an Incomplete verdict lists its reason as a fix row`() {
        showIssues(Verdict.Incomplete(IncompleteReason(TangoDraftAnalyzer.EMPTY, 0)), emptyList())

        assertEquals1("tango.editor.fix.${TangoDraftAnalyzer.EMPTY}")
    }

    @Test
    fun `a needs-guessing block lists as a fix row`() {
        showIssues(
            Verdict.Unique(Solution(emptyList()), needsGuessing = true),
            listOf(PublishBlock(PublishBlock.NEEDS_GUESSING)),
        )

        assertEquals1("tango.editor.fix.${PublishBlock.NEEDS_GUESSING}")
    }

    @Test
    fun `a clean verdict draws no fix rows`() {
        showIssues(Verdict.Undetermined, emptyList())

        assertTrue(count("tango.editor.fix") == 0)
    }

    private fun showIssues(verdict: Verdict, blocks: List<PublishBlock>) {
        rule.setContent {
            PuzzleBoxTheme { TangoEditorUi.Issues(controller, verdict, blocks, onSelect = {}, modifier = Modifier) }
        }
    }

    private fun assertEquals1(tag: String) = rule.onAllNodesWithTag(tag).assertCountEquals(1)
}
