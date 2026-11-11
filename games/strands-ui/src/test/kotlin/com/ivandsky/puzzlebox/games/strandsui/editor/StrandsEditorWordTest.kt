package com.ivandsky.puzzlebox.games.strandsui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.strands.StrandsDefinition
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraft
import com.ivandsky.puzzlebox.games.strands.editor.StrandsEditor
import com.ivandsky.puzzlebox.gameui.PlayLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Typing into the word field and the dictionary check, through the tags. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class StrandsEditorWordTest {

    @get:Rule
    val rule = createComposeRule()

    private val edits = mutableListOf<EditorDraft<*>>()
    private val controller = StrandsEditorController(EditorDraft.fresh(StrandsEditor, StrandsDefinition.VARIANT)) {
        edits += it
    }

    private fun board() = controller.draft.value.state.draft as StrandsDraft

    private val queue = QueueDispatcher()

    private fun show() = rule.setContent {
        PuzzleBoxTheme {
            CompositionLocalProvider(LocalEditorDispatchers provides QueueDispatcherProvider(queue)) {
                Column { StrandsEditorUi.Board(controller, Verdict.Checking, PlayLayout.SinglePane, Modifier.weight(1f)) }
            }
        }
    }

    /** Lets the queued dictionary lookups run, then the recomposition they cause. */
    private fun answerLookups() {
        rule.waitForIdle()
        queue.runAll()
        rule.waitForIdle()
    }

    private fun tap(vararg cells: Int) = cells.forEach {
        rule.onNodeWithTag("strands.editor.cell.$it").performClick()
    }

    /** A four-cell unnamed path along the top row, selected. */
    private fun selectedPath() {
        show()
        tap(0, 1, 2, 3, 3)
        tap(0)
        assertEquals(0, controller.selectedPath.value)
    }

    private fun SemanticsNodeInteraction.state(): String? =
        fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)

    private fun showPane() = rule.setContent {
        PuzzleBoxTheme {
            CompositionLocalProvider(LocalEditorDispatchers provides QueueDispatcherProvider(queue)) {
                StrandsEditorUi.PaneTools(controller, Modifier)
            }
        }
    }

    private fun isError(tag: String): Boolean =
        rule.onNodeWithTag(tag).fetchSemanticsNode().config.contains(SemanticsProperties.Error)

    @Test
    fun `a word outside the dictionary warns on the phone and is no error`() {
        selectedPath()
        controller.typeWord("xqzv")
        answerLookups()

        assertEquals(WordCheck.Unknown.name, rule.onNodeWithTag("strands.editor.word.check", useUnmergedTree = true).state())
        assertFalse(isError("strands.editor.word"))
        assertEquals("XQZV", board().paths.single().word)
    }

    @Test
    fun `a word outside the dictionary warns in the tablet pane and is no error`() {
        controller.traceTo(0)
        controller.traceTo(1)
        controller.traceTo(2)
        controller.traceTo(3)
        controller.endTrace()
        controller.selectPath(0)
        showPane()
        controller.typeWord("xqzv")
        answerLookups()

        assertEquals(WordCheck.Unknown.name, rule.onNodeWithTag("strands.editor.word.check", useUnmergedTree = true).state())
        assertFalse(isError("strands.editor.word"))
    }

    @Test
    fun `a word longer than its path is still an error`() {
        selectedPath()
        controller.typeWord("stars")
        answerLookups()

        assertTrue(isError("strands.editor.word"))
    }

    @Test
    fun `typing a word writes its letters onto the selected path and names it`() {
        selectedPath()
        rule.onNodeWithTag("strands.editor.word").performTextInput("star")

        assertEquals("STAR", board().letters.take(4))
        assertEquals("STAR", board().paths.single().word)
    }

    @Test
    fun `each word change is one undo step`() {
        selectedPath()
        val before = edits.size
        controller.typeWord("s")
        controller.typeWord("st")

        assertEquals(before + 2, edits.size)
        val once = controller.draft.value.undo()!!
        assertEquals("S...", (once.state.draft as StrandsDraft).letters.take(4))
        assertEquals("....", (once.undo()!!.state.draft as StrandsDraft).letters.take(4))
    }

    @Test
    fun `retyping the same text is no edit`() {
        selectedPath()
        controller.typeWord("star")
        val before = edits.size
        controller.typeWord("star")

        assertEquals(before, edits.size)
    }

    @Test
    fun `clearing the field blanks the cells the typing wrote`() {
        selectedPath()
        rule.onNodeWithTag("strands.editor.word").performTextInput("star")
        rule.onNodeWithTag("strands.editor.word").performTextClearance()

        assertEquals("....", board().letters.take(4))
        assertNull(board().paths.single().word)
    }

    @Test
    fun `the dictionary check waits for its dispatcher and never blocks typing`() {
        selectedPath()
        rule.onNodeWithTag("strands.editor.word").performTextInput("star")

        assertEquals("STAR", board().paths.single().word)
        assertEquals(WordCheck.Empty.name, rule.onNodeWithTag("strands.editor.word.check", useUnmergedTree = true).state())
        assertTrue(queue.pending > 0)
        answerLookups()
        assertEquals(WordCheck.Word.name, rule.onNodeWithTag("strands.editor.word.check", useUnmergedTree = true).state())
    }

    @Test
    fun `the dictionary check follows the text, an icon on the phone that describes itself`() {
        selectedPath()
        rule.onNodeWithTag("strands.editor.word").performTextInput("sta")
        answerLookups()
        assertDescribedCheck(WordCheck.Prefix)
        controller.typeWord("star")
        answerLookups()
        assertDescribedCheck(WordCheck.Word)
        controller.typeWord("xqzv")
        answerLookups()
        assertDescribedCheck(WordCheck.Unknown)
    }

    @Test
    fun `on the phone the field is one box, its overflow line under it only while too long`() {
        selectedPath()
        val field = rule.onNodeWithTag("strands.editor.word").getUnclippedBoundsInRoot()
        // One text box: a floating label or a resting supporting line would each add a line.
        assertTrue("word field ${field.height} taller than one box", field.height <= ONE_BOX)
        rule.onNodeWithTag("strands.editor.word.label", useUnmergedTree = true).assertIsDisplayed()

        controller.typeWord("stars")
        answerLookups()

        val fit = rule.onNodeWithTag("strands.editor.word.fit", useUnmergedTree = true).assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue("fit line $fit not under the field $field", fit.top >= field.bottom - 0.5.dp)
    }

    @Test
    fun `on the phone the field keeps its name once a word hides the placeholder`() {
        selectedPath()
        val name = rule.onNodeWithTag("strands.editor.word.label", useUnmergedTree = true).text()

        controller.typeWord("sta")
        answerLookups()

        rule.onNodeWithTag("strands.editor.word.label", useUnmergedTree = true).assertDoesNotExist()
        val description = rule.onNodeWithTag("strands.editor.word").fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)
        assertTrue("field named $description, not $name", description.orEmpty().contains(name))
    }

    @Test
    fun `the off field's hint stays readable, 4_5 to 1 against what is behind it`() {
        show()
        answerLookups()
        val pixels = rule.onNodeWithTag("strands.editor.word.label", useUnmergedTree = true).captureToImage().toPixelMap()
        val luminances = (0 until pixels.width).flatMap { x -> (0 until pixels.height).map { y -> pixels[x, y].luminance() } }
        val contrast = (luminances.max() + 0.05f) / (luminances.min() + 0.05f)
        assertTrue("hint contrast $contrast under 4.5:1", contrast >= 4.5f)
    }

    /** The check node in [expected] state, with a description to read it by. */
    private fun assertDescribedCheck(expected: WordCheck) {
        val check = rule.onNodeWithTag("strands.editor.word.check", useUnmergedTree = true)
        assertEquals(expected.name, check.state())
        val description = check.fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)
        assertTrue("no description for $expected", !description.isNullOrEmpty())
    }

    @Test
    fun `with no selection the field is off and says to trace a word first`() {
        show()
        rule.onNodeWithTag("strands.editor.word").assertIsNotEnabled()
        answerLookups()
        assertEquals(WordCheck.NoPath.name, rule.onNodeWithTag("strands.editor.word.check", useUnmergedTree = true).state())
        rule.onNodeWithTag("strands.editor.word.label", useUnmergedTree = true).assertIsDisplayed()
        assertEquals("", controller.word.value)
    }

    private fun SemanticsNodeInteraction.text(): String =
        fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString { it.text }

    @Test
    fun `a lift selects the new path and the field then names it with no extra tap`() {
        show()
        tap(0, 1, 1)

        assertEquals(0, controller.selectedPath.value)
        rule.onNodeWithTag("strands.editor.word").assertIsEnabled()
        rule.onNodeWithTag("strands.editor.word").performTextInput("go")

        assertEquals("GO", board().paths.single().word)
        assertEquals("GO", board().letters.take(2))
    }

    @Test
    fun `text longer than the path stays in the field, is flagged, and writes only what fits`() {
        show()
        tap(0, 1, 1)
        rule.onNodeWithTag("strands.editor.word").performTextInput("gold")
        answerLookups()

        rule.onNodeWithTag("strands.editor.word").assertTextContains("GOLD")
        val fit = rule.onNodeWithTag("strands.editor.word.fit", useUnmergedTree = true)
        assertEquals("TooLong", fit.state())
        fit.assertTextEquals(StrandsEditorCopy.overflow(4, 2))
        rule.onNodeWithTag("strands.editor.word.check", useUnmergedTree = true).assertDoesNotExist()
        assertEquals("GO", board().letters.take(2))
        assertNull(board().paths.single().word)

        rule.onNodeWithTag("strands.editor.word").performTextClearance()
        rule.onNodeWithTag("strands.editor.word").performTextInput("go")
        rule.onNodeWithTag("strands.editor.word.fit", useUnmergedTree = true).assertDoesNotExist()
        assertEquals("GO", board().paths.single().word)
    }

    private companion object {
        /** The outlined box's 56dp floor with the theme's body line: well under a box plus a label or a line. */
        val ONE_BOX = 60.dp
    }
}
