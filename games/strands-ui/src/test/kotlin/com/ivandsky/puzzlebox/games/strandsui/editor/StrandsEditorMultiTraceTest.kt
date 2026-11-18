package com.ivandsky.puzzlebox.games.strandsui.editor

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertTextContains
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.Budget
import com.ivandsky.puzzlebox.game.editor.DraftState
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraft
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraftAnalyzer
import com.ivandsky.puzzlebox.games.strands.editor.StrandsDraftPath
import com.ivandsky.puzzlebox.games.strands.editor.StrandsEditor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The fix list's warning row for a word that can be traced several ways over its cells. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StrandsEditorMultiTraceTest {

    @get:Rule
    val rule = createComposeRule()

    private fun show(word: String) {
        val letters = word + ".".repeat(StrandsDraft.ROWS * StrandsDraft.COLS - word.length)
        val draft = StrandsDraft(letters, "clue", listOf(StrandsDraftPath(word.indices.toList(), word)), 0)
        val controller = StrandsEditorController(EditorDraft(StrandsEditor, DraftState(draft))) {}
        val verdict = StrandsDraftAnalyzer.analyze(draft, Budget.Unlimited)
        rule.setContent { PuzzleBoxTheme { StrandsEditorUi.Issues(controller, verdict, emptyList(), {}, Modifier) } }
    }

    @Test
    fun `a word traceable both ways round gets a warning row naming it`() {
        show("ABCCBA")

        rule.onNodeWithTag("strands.editor.fix.multi-trace").assertExists()
        rule.onNodeWithTag("strands.editor.fix.multi-trace").assertTextContains(StrandsEditorCopy.multiTrace("ABCCBA"))
    }

    @Test
    fun `a word with one trace gets no warning row`() {
        show("ABCDEF")

        rule.onNodeWithTag("strands.editor.fix.multi-trace").assertDoesNotExist()
    }
}
