package com.ivandsky.puzzlebox.games.sudokuui.editor

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertCountEquals
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.game.editor.Solution
import com.ivandsky.puzzlebox.game.editor.Verdict
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.editor.SudokuEditor
import com.ivandsky.puzzlebox.games.sudoku.editor.SudokuForcedCells
import com.ivandsky.puzzlebox.gameui.editor.EditorOverlay
import kotlinx.coroutines.CoroutineDispatcher
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.coroutines.CoroutineContext

/** Overlay digits stay out of TalkBack; their meaning rides the tagged node as state. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class SudokuEditorA11yTest {

    @get:Rule
    val rule = createComposeRule()

    private val queue = A11yQueueDispatcher()
    private val editor = SudokuEditor(SudokuGames.MINI)

    private fun controllerOf(givens: Map<Int, Int>): SudokuEditorController {
        val blank = EditorDraft.fresh(editor, editor.variants.first())
        val cells = List(CELLS) { givens[it] ?: 0 }
        return SudokuEditorController(
            EditorDraft(editor, blank.state.copy(draft = blank.state.draft.copy(givens = cells))),
        ) {}
    }

    private fun show(controller: SudokuEditorController, verdict: Verdict) {
        rule.setContent {
            PuzzleBoxTheme {
                CompositionLocalProvider(LocalSudokuEditorDispatchers provides A11yA11yQueueDispatcherProvider(queue)) {
                    SudokuEditorBoard(controller, SudokuGames.MINI.variant.geometry, verdict = verdict)
                }
            }
        }
        rule.waitForIdle()
        queue.runAll()
        rule.waitForIdle()
    }

    @Test
    fun `a witness digit is hidden from TalkBack and spoken as answer state`() {
        val controller = controllerOf(emptyMap())
        controller.adoptOverlay(EditorOverlay(EditorOverlay.Witness.A, showOnBoard = true))
        show(controller, MULTIPLE)

        rule.onNodeWithTag("sudoku.editor.cell.$DIFFERING.witness").assert(
            spokenAs("answer ${A.values[DIFFERING]}"),
        )
    }

    @Test
    fun `a forced digit is hidden from TalkBack and spoken as forced state`() {
        val blank = EditorDraft.fresh(editor, editor.variants.first())
        val draft = blank.state.draft.copy(givens = MINI_GIVENS.map { if (it == '.') 0 else it.digitToInt() })
        val controller = SudokuEditorController(EditorDraft(editor, blank.state.copy(draft = draft))) {}
        controller.toggleForcedView()
        show(controller, Verdict.Checking)

        val (idx, digit) = SudokuForcedCells.of(draft).entries.first()
        assertTrue(rule.onAllNodesWithTag("sudoku.editor.cell.$idx.forced").fetchSemanticsNodes().isNotEmpty())
        rule.onNodeWithTag("sudoku.editor.cell.$idx.forced").assert(spokenAs("forced $digit"))
    }

    @Test
    fun `the unpaired badge carries a content description`() {
        val controller = controllerOf(emptyMap())
        controller.tap(0)
        controller.digit(3)
        controller.cycleSymmetry()
        show(controller, Verdict.Checking)

        rule.onAllNodesWithTag("sudoku.editor.unpaired").assertCountEquals(1)
        rule.onNodeWithTag("sudoku.editor.unpaired").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Unpaired cells: 1")),
        )
    }

    /** The tagged node speaks only its state: a state description and no stray digit text. */
    private fun spokenAs(state: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, state)
            .and(hasNoTextProperty)

    private val hasNoTextProperty =
        SemanticsMatcher("has no text property") { !it.config.contains(SemanticsProperties.Text) }

    private infix fun SemanticsMatcher.and(other: SemanticsMatcher) = SemanticsMatcher(
        "${this.description} and ${other.description}",
    ) { value -> this.matches(value) && other.matches(value) }

    private companion object {
        const val CELLS = 36
        const val DIFFERING = 2
        const val MINI_GIVENS = "....62..643..2.5.3..1....3...4.65.2."
        val A = Solution(List(CELLS) { it % 6 + 1 })
        val B = Solution(List(CELLS) { if (it < 4) (it + 1) % 6 + 1 else it % 6 + 1 })
        val MULTIPLE = Verdict.Multiple(A, B, (0 until 4).toSet())
    }
}

/** A dispatcher that holds its work until [runAll]; nothing runs behind the test's back. */
private class A11yQueueDispatcher : CoroutineDispatcher() {
    private val queue = ArrayDeque<Runnable>()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        queue.addLast(block)
    }

    /** Runs queued work, including work it queues, until none is left. */
    fun runAll() {
        while (queue.isNotEmpty()) queue.removeFirst().run()
    }
}

private class A11yA11yQueueDispatcherProvider(queue: A11yQueueDispatcher) : DispatcherProvider {
    override val main: CoroutineDispatcher = queue
    override val io: CoroutineDispatcher = queue
    override val default: CoroutineDispatcher = queue
}
