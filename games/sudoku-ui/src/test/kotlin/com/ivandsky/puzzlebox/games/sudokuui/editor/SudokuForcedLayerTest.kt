package com.ivandsky.puzzlebox.games.sudokuui.editor

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.ivandsky.puzzlebox.common.dispatcher.DispatcherProvider
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.editor.EditorDraft
import com.ivandsky.puzzlebox.games.sudoku.SudokuDefinition
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.editor.SudokuDraft
import com.ivandsky.puzzlebox.games.sudoku.editor.SudokuEditor
import com.ivandsky.puzzlebox.games.sudoku.editor.SudokuForcedCells
import kotlinx.coroutines.CoroutineDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.coroutines.CoroutineContext

/** The Forced cells toggle shows what logic places from the givens and edits nothing. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class SudokuForcedLayerTest {

    @get:Rule
    val rule = createComposeRule()

    private val edits = ArrayList<EditorDraft<*>>()
    private val queue = QueueDispatcher()

    private fun grid(text: String): List<Int> = text.map { if (it == '.') 0 else it.digitToInt() }

    private fun open(game: SudokuDefinition, givens: String): Pair<SudokuEditorController, SudokuDraft> {
        val editor = SudokuEditor(game)
        val blank = EditorDraft.fresh(editor, editor.variants.first())
        val draft = blank.state.draft.copy(givens = grid(givens))
        val controller = SudokuEditorController(EditorDraft(editor, blank.state.copy(draft = draft))) { edits += it }
        rule.setContent {
            PuzzleBoxTheme {
                CompositionLocalProvider(LocalSudokuEditorDispatchers provides QueueDispatcherProvider(queue)) {
                    SudokuEditorBoard(controller, game.variant.geometry)
                }
            }
        }
        return controller to draft
    }

    /** Lets the queued walks run, then the recomposition they cause. */
    private fun answerWalks() {
        rule.waitForIdle()
        queue.runAll()
        rule.waitForIdle()
    }

    private fun forcedTags(size: Int): Set<Int> = (0 until size * size)
        .filter { rule.onAllNodesWithTag("sudoku.editor.cell.$it.forced").fetchSemanticsNodes().isNotEmpty() }
        .toSet()

    private fun assertToggle(on: Boolean) {
        rule.onNodeWithTag("sudoku.editor.forced").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, if (on) "On" else "Off"),
        )
    }

    private fun checkToggle(game: SudokuDefinition, givens: String, size: Int) {
        val (controller, draft) = open(game, givens)
        val expected = SudokuForcedCells.of(draft).keys
        assertTrue(expected.isNotEmpty())
        assertToggle(on = false)
        assertEquals(emptySet<Int>(), forcedTags(size))

        controller.toggleForcedView()
        answerWalks()
        assertEquals(expected, forcedTags(size))
        assertToggle(on = true)

        controller.toggleForcedView()
        answerWalks()
        assertEquals(emptySet<Int>(), forcedTags(size))
        assertToggle(on = false)
    }

    @Test
    fun `the toggle tags exactly the forced cells at 6x6 and clears them off`() {
        checkToggle(SudokuGames.MINI, MINI_GIVENS, size = 6)
    }

    @Test
    fun `the toggle tags exactly the forced cells at 9x9 and clears them off`() {
        checkToggle(SudokuGames.CLASSIC, CLASSIC_GIVENS, size = 9)
    }

    @Test
    fun `the toggle button flips the view`() {
        val (controller, _) = open(SudokuGames.MINI, MINI_GIVENS)

        rule.onNodeWithTag("sudoku.editor.forced").performClick()

        assertTrue(controller.forcedView.value)
        assertToggle(on = true)
    }

    @Test
    fun `clashing givens show nothing`() {
        val (controller, _) = open(SudokuGames.MINI, "3....3" + ".".repeat(30))

        controller.toggleForcedView()
        answerWalks()

        assertEquals(emptySet<Int>(), forcedTags(6))
    }

    @Test
    fun `a forced cell takes its digit from the keypad as one edit`() {
        val (controller, draft) = open(SudokuGames.MINI, MINI_GIVENS)
        val (cell, digit) = SudokuForcedCells.of(draft).entries.first()
        controller.toggleForcedView()
        answerWalks()
        assertTrue(cell in forcedTags(6))

        rule.onNodeWithTag("sudoku.cell.$cell").performClick()
        rule.onNodeWithTag("sudoku.editor.key.$digit").performClick()

        assertEquals(1, edits.size)
        assertEquals(digit, ((edits.single().state.draft) as SudokuDraft).givens[cell])
    }

    @Test
    fun `viewing forced cells adds no edit`() {
        val (controller, _) = open(SudokuGames.MINI, MINI_GIVENS)

        controller.toggleForcedView()
        answerWalks()

        assertTrue(edits.isEmpty())
    }

    private companion object {
        const val MINI_GIVENS = "....62..643..2.5.3..1....3...4.65.2."
        const val CLASSIC_GIVENS =
            "53..7....6..195....98....6.8...6...34..8.3..17...2...6.6....28....419..5....8..79"
    }
}

/** A dispatcher that holds its work until [runAll]; nothing runs behind the test's back. */
private class QueueDispatcher : CoroutineDispatcher() {
    private val queue = ArrayDeque<Runnable>()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        queue.addLast(block)
    }

    /** Runs queued work, including work it queues, until none is left. */
    fun runAll() {
        while (queue.isNotEmpty()) queue.removeFirst().run()
    }
}

private class QueueDispatcherProvider(queue: QueueDispatcher) : DispatcherProvider {
    override val main: CoroutineDispatcher = queue
    override val io: CoroutineDispatcher = queue
    override val default: CoroutineDispatcher = queue
}
