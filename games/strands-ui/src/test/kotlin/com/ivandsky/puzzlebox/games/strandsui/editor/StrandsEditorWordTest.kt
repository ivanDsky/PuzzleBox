package com.ivandsky.puzzlebox.games.strandsui.editor

import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.RepeatableSpec
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.strands.StrandsDefinition
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraft
import com.ivandsky.puzzlebox.games.strands.editor.StrandsEditor
import com.ivandsky.puzzlebox.gameui.PlayLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Typing into the word field, the dictionary check, and the "+N" handle, through the tags. */
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
    fun `the dictionary check follows the text`() {
        selectedPath()
        rule.onNodeWithTag("strands.editor.word").performTextInput("sta")
        answerLookups()
        assertEquals(WordCheck.Prefix.name, rule.onNodeWithTag("strands.editor.word.check", useUnmergedTree = true).state())
        controller.typeWord("star")
        answerLookups()
        assertEquals(WordCheck.Word.name, rule.onNodeWithTag("strands.editor.word.check", useUnmergedTree = true).state())
        controller.typeWord("xqzv")
        answerLookups()
        assertEquals(WordCheck.Unknown.name, rule.onNodeWithTag("strands.editor.word.check", useUnmergedTree = true).state())
    }

    @Test
    fun `a longer word shows how many cells are missing and each tap extends by one`() {
        selectedPath()
        rule.onNodeWithTag("strands.editor.extend").assertDoesNotExist()
        rule.onNodeWithTag("strands.editor.word").performTextInput("starsx")

        assertNull(board().paths.single().word)
        assertEquals("2", rule.onNodeWithTag("strands.editor.extend").state())

        rule.onNodeWithTag("strands.editor.extend").performClick()
        assertEquals(5, board().paths.single().cells.size)
        assertEquals("1", rule.onNodeWithTag("strands.editor.extend").state())
        assertEquals("S", board().letters[board().paths.single().cells.last()].toString())

        rule.onNodeWithTag("strands.editor.extend").performClick()
        assertEquals(6, board().paths.single().cells.size)
        assertEquals("STARSX", board().paths.single().word)
        rule.onNodeWithTag("strands.editor.extend").assertDoesNotExist()
    }

    @Test
    fun `one extend is one undo step`() {
        selectedPath()
        controller.typeWord("stars")
        val before = edits.size
        controller.extend()

        assertEquals(before + 1, edits.size)
        val undone = controller.draft.value.undo()!!.state.draft as StrandsDraft
        assertEquals(4, undone.paths.single().cells.size)
        assertEquals("STAR", undone.letters.take(4))
        assertEquals("....", undone.letters.drop(4).take(4))
    }

    @Test
    fun `extend skips cells other paths hold`() {
        show()
        tap(4, 10, 10)
        tap(0, 1, 2, 3, 3)
        tap(0)
        controller.typeWord("stars")
        controller.extend()

        assertEquals(listOf(0, 1, 2, 3, 8), board().paths.last().cells)
    }

    @Test
    fun `extend with nothing missing or nothing selected changes nothing`() {
        show()
        controller.extend()
        assertTrue(edits.isEmpty())
        tap(0, 1, 2, 3, 3)
        tap(0)
        controller.typeWord("star")
        val before = edits.size
        controller.extend()

        assertEquals(before, edits.size)
    }

    @Test
    fun `the handle animates a finite number of times`() {
        val spec = ExtendHandleMotion.spec

        assertFalse(spec is InfiniteRepeatableSpec<*>)
        assertTrue(spec is RepeatableSpec<*> && spec.iterations == 1)
    }

    @Test
    fun `without a selection the typed word waits for the next lift`() {
        show()
        rule.onNodeWithTag("strands.editor.word").performTextInput("go")
        tap(0, 1, 1)

        assertEquals("GO", board().paths.single().word)
        assertEquals("", controller.word.value)
    }
}
