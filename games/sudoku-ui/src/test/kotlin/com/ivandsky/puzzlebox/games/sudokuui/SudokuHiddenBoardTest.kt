package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import android.view.View
import android.view.accessibility.AccessibilityNodeProvider
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.gameui.hidden.HiddenBoard
import com.ivandsky.puzzlebox.model.GameId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuSamples
import com.ivandsky.puzzlebox.games.sudoku.codec.SudokuCodec
import com.ivandsky.puzzlebox.games.sudoku.digit

/**
 * Sudoku's board before play shows the given digits and nothing else: the solved grid is dropped before a session
 * exists, so no board can draw a solved cell and no reader can speak one.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class SudokuHiddenBoardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var view: View

    private fun envelopeOf(puzzle: SudokuPuzzle) =
        checkNotNull(SudokuGames.CLASSIC.decode(SudokuCodec.encode(puzzle)).getOrNull())

    private val classic = SudokuSamples.classic
    private val envelope = envelopeOf(classic)

    @Test
    fun `the puzzle as it travels does carry its solution`() {
        assertEquals(classic.solution, SudokuCodec.decode(envelope.payload).getOrNull()?.solution)
    }

    @Test
    fun `the board is built from the given digits alone`() {
        val session = checkNotNull(sudokuGivensSession(envelope))
        assertEquals(classic.givens, session.puzzle.givens)
        assertEquals(classic.givens, session.board.cells.map { it.digit })
        // Where the puzzle gives no digit, the session knows no digit either.
        val blanks = classic.givens.indices.filter { classic.givens[it] == 0 }
        assertEquals(1, blanks.map { session.puzzle.solution[it] }.distinct().size)
        assertNotEquals(blanks.map { classic.solution[it] }, blanks.map { session.puzzle.solution[it] })
    }

    @Test
    fun `two puzzles that differ only in their solution draw the same board`() {
        val other = envelopeOf(classic.copy(solution = classic.solution.map { it % 9 + 1 }))
        assertNotEquals(envelope.payload.toList(), other.payload.toList())
        assertEquals(sudokuGivensSession(envelope), sudokuGivensSession(other))
    }

    @Test
    fun `the mini game's board is drawn from the same seam`() {
        val givens = List(36) { if (it % 7 == 0) it % 6 + 1 else 0 }
        val mini = SudokuPuzzle(SudokuGames.MINI.variant, givens, List(36) { it % 6 + 1 })
        val miniEnvelope = checkNotNull(SudokuGames.MINI.decode(SudokuCodec.encode(mini)).getOrNull())
        val board = checkNotNull(SudokuReplayBoard.givens(miniEnvelope))
        assertEquals(6 to 6, board.rows to board.cols)
        assertEquals(givens, checkNotNull(sudokuGivensSession(miniEnvelope)).puzzle.givens)
    }

    @Test
    fun `a classic payload under the mini game's name draws nothing`() {
        assertNull(SudokuReplayBoard.givens(Puzzle(SudokuGames.MINI.id, envelope.variant, envelope.formatVersion, envelope.payload)))
    }

    @Test
    fun `the slot takes the board's shape`() {
        val board = checkNotNull(SudokuReplayBoard.givens(envelope))
        assertEquals(9 to 9, board.rows to board.cols)
    }

    /** Every text and description in the tree as a reader of the screen takes it: merged. */
    private fun spoken(): List<String> {
        val out = ArrayList<String>()
        fun walk(node: SemanticsNode) {
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.let(out::addAll)
            node.config.getOrNull(SemanticsProperties.Text)?.forEach { out += it.text }
            node.children.forEach(::walk)
        }
        walk(composeRule.onRoot().fetchSemanticsNode())
        return out
    }

    /**
     * What the platform will describe to an accessibility service: the description and text of every node it
     * agrees to hand out, asking for each node of the unmerged tree by its id. The slot clears what is under it
     * rather than removing it, so the board's nodes are still in that tree; none of them is handed out.
     */
    private fun handedOut(): List<String> {
        val root = composeRule.onRoot(useUnmergedTree = true).fetchSemanticsNode()
        val out = ArrayList<String>()
        composeRule.runOnUiThread {
            val provider = checkNotNull(view.accessibilityNodeProvider)
            fun ask(node: SemanticsNode, id: Int) {
                // A node that merges its children is described by two stand-ins the tree does not list.
                for (asked in listOf(id, id + ROLE_STAND_IN, id + DESCRIPTION_STAND_IN)) {
                    val info = provider.createAccessibilityNodeInfo(asked) ?: continue
                    info.contentDescription?.let { out += it.toString() }
                    info.text?.let { out += it.toString() }
                }
                node.children.forEach { ask(it, it.id) }
            }
            ask(root, AccessibilityNodeProvider.HOST_VIEW_ID)
        }
        return out
    }

    /** The cell has room past its text: a described cell its own text covers whole is handed out as text alone. */
    @Test
    fun `shown bare, a cell that says what it holds is handed to a screen reader`() {
        composeRule.setContent {
            view = LocalView.current
            PuzzleBoxTheme {
                Box(Modifier.size(96.dp).semantics { contentDescription = "Cell" }.clickable { }) { Text("7") }
            }
        }
        composeRule.waitForIdle()
        assertEquals(listOf("Cell", "7"), handedOut())
    }

    private fun show(sharp: Boolean) {
        val board = checkNotNull(SudokuReplayBoard.givens(envelope))
        composeRule.setContent {
            view = LocalView.current
            PuzzleBoxTheme { HiddenBoard(board, sharp = sharp) }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `blurred, the screen reader hears one line and no cell`() {
        show(sharp = false)
        assertEquals(listOf("Board hidden until you play"), handedOut())
        assertEquals(listOf("Board hidden until you play"), spoken())
    }

    @Test
    fun `sharp, the screen reader still hears one line and no cell`() {
        show(sharp = true)
        assertEquals(listOf("Board"), handedOut())
        assertEquals(listOf("Board"), spoken())
    }

    @Test
    fun `another game's puzzle and a broken payload draw nothing`() {
        assertNull(SudokuReplayBoard.givens(Puzzle(GameId("someone-else"), envelope.variant, envelope.formatVersion, envelope.payload)))
        assertNull(SudokuReplayBoard.givens(Puzzle(envelope.gameId, envelope.variant, envelope.formatVersion, byteArrayOf(9, 9, 9))))
    }

    private companion object {
        const val ROLE_STAND_IN = 1_000_000_000
        const val DESCRIPTION_STAND_IN = 2_000_000_000
    }
}
