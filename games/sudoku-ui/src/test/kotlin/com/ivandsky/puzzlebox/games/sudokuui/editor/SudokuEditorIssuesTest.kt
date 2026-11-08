package com.ivandsky.puzzlebox.games.sudokuui.editor

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.IncompleteReason
import com.ivandsky.puzzlebox.game.editor.PublishBlock
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.editor.SudokuDraftAnalyzer
import com.ivandsky.puzzlebox.games.sudoku.editor.SudokuEditor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The fix list names each Incomplete reason and block, and draws nothing when clean. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w420dp-h900dp-420dpi")
class SudokuEditorIssuesTest {

    @get:Rule
    val rule = createComposeRule()

    private val editor = SudokuEditor(SudokuGames.MINI)
    private val ui = SudokuEditorUi(SudokuGames.MINI)
    private val controller =
        SudokuEditorController(EditorDraft.fresh(editor, editor.variants.first())) {}

    @Test
    fun `an Incomplete verdict lists its reason as a fix row`() {
        showIssues(Verdict.Incomplete(IncompleteReason(SudokuDraftAnalyzer.EMPTY, 0)), emptyList())

        rule.onAllNodesWithTag("sudoku.editor.fix.${SudokuDraftAnalyzer.EMPTY}").assertCountEquals(1)
    }

    @Test
    fun `a needs-guessing block lists as a fix row`() {
        showIssues(
            Verdict.Unique(Solution(emptyList()), needsGuessing = true),
            listOf(PublishBlock(PublishBlock.NEEDS_GUESSING)),
        )

        rule.onAllNodesWithTag("sudoku.editor.fix.${PublishBlock.NEEDS_GUESSING}").assertCountEquals(1)
    }

    @Test
    fun `a valid verdict with no blocks draws no fix rows`() {
        showIssues(Verdict.Unique(Solution(emptyList()), needsGuessing = false), emptyList())

        rule.onAllNodesWithTag("sudoku.editor.fix").assertCountEquals(0)
    }

    private fun showIssues(verdict: Verdict, blocks: List<PublishBlock>) {
        rule.setContent {
            PuzzleBoxTheme {
                ui.Issues(controller, verdict, blocks, onSelect = {}, modifier = Modifier)
            }
        }
    }
}
