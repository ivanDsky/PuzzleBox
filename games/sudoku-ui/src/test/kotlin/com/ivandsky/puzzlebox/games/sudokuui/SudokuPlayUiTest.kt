package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzleRef
import com.ivandsky.puzzlebox.games.sudoku.SudokuSamples
import com.ivandsky.puzzlebox.games.sudoku.SudokuSession
import com.ivandsky.puzzlebox.games.sudoku.codec.SudokuCodec
import com.ivandsky.puzzlebox.games.sudoku.digit
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class SudokuPlayUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val givens = grid("..3.5. .2.4.6 1..... .....2 2.4.1. .1.3..")
    private val solution = grid("463251 521436 142563 356142 234615 615324")
    private val peanut450 = SudokuPuzzle(SudokuGames.MINI.variant, givens, solution)
    private val ref = SudokuPuzzleRef(SudokuGames.MINI.id, PuzzleKey.Daily(LocalDate.of(2026, 11, 4), 450))
    private val ui = SudokuPlayUi(SudokuGames.MINI, InMemorySudokuHighlightSettings())
    private val solved = ArrayList<Pair<Long, Int>>()

    private fun grid(text: String): List<Int> = text.filterNot { it.isWhitespace() }.map { if (it == '.') 0 else it.digitToInt() }

    private fun adopted(game: SudokuPlayUi = ui, puzzle: SudokuPuzzle = peanut450, puzzleRef: SudokuPuzzleRef = ref): SudokuPlayController {
        val controller = game.createController(PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), {}, {})) as SudokuPlayController
        val envelope = Puzzle(game.gameId, puzzle.variant.id, SudokuCodec.VERSION, SudokuCodec.encode(puzzle))
        assertEquals(Adopt.Adopted, controller.adoptToday(envelope, puzzleRef))
        return controller
    }

    private fun setScreen(controller: SudokuPlayController, layout: PlayLayout, game: SudokuPlayUi = ui) {
        composeRule.setContent {
            PuzzleBoxTheme {
                game.Screen(
                    controller = controller,
                    status = PlayBoardStatus.Ready(banner = null),
                    layout = layout,
                    sidePanel = null,
                    onRetry = {},
                    onSolved = { time, hints -> solved += time to hints },
                    modifier = Modifier,
                )
            }
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-mdpi")
    fun `the 6x6 fits a 360x640 phone with grid, keypad and chrome apart`() {
        assertFitsSinglePane(ui, adopted(), size = 6)
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-mdpi")
    fun `the 9x9 fits a 360x640 phone with grid, keypad and chrome apart`() {
        val classic = SudokuPlayUi(SudokuGames.CLASSIC, InMemorySudokuHighlightSettings())
        assertFitsSinglePane(classic, adopted(classic, SudokuSamples.classic, SudokuPuzzleRef(SudokuGames.CLASSIC.id, PuzzleKey.Daily((ref.key as PuzzleKey.Daily).date, null))), size = 9)
    }

    @Test
    fun `the 6x6 cell takes all the height the 411x891 chrome leaves`() {
        assertFitsSinglePane(ui, adopted(), size = 6)
    }

    @Test
    fun `the 9x9 cell takes all the height the 411x891 chrome leaves`() {
        val classic = SudokuPlayUi(SudokuGames.CLASSIC, InMemorySudokuHighlightSettings())
        assertFitsSinglePane(classic, adopted(classic, SudokuSamples.classic, SudokuPuzzleRef(SudokuGames.CLASSIC.id, PuzzleKey.Daily((ref.key as PuzzleKey.Daily).date, null))), size = 9)
    }

    /** Grid, keypad and chrome stack apart on screen, sized by the balanced rule for the board pane (R1). */
    private fun assertFitsSinglePane(game: SudokuPlayUi, controller: SudokuPlayController, size: Int) {
        setScreen(controller, PlayLayout.SinglePane, game)
        val grid = composeRule.onNodeWithTag("sudoku.grid").getUnclippedBoundsInRoot()
        val keypad = composeRule.onNodeWithTag("sudoku.keypad").getUnclippedBoundsInRoot()
        val area = composeRule.onNodeWithTag("sudoku.board_area").getUnclippedBoundsInRoot()
        val chrome = composeRule.onNodeWithTag("play.side_panel").getUnclippedBoundsInRoot()
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        assertTrue("keypad under grid ($grid, $keypad)", keypad.top >= grid.bottom)
        assertTrue("chrome under keypad ($keypad, $chrome)", chrome.top >= keypad.bottom)
        assertTrue("chrome on screen ($chrome, $root)", chrome.bottom <= root.bottom)

        val layout = SudokuSizing.layout(area.width, area.height, size)
        println("R1 ${size}x$size: grid=${grid.width} keypad=${keypad.height} area=${area.width}x${area.height} chrome=${chrome.height} $layout")
        assertEquals(layout.board.value, grid.width.value, 0.5f)
        assertEquals(SudokuSizing.keypadHeight(layout).value, keypad.height.value, 1f) // per-row px rounding at 420dpi
    }

    private fun session(controller: SudokuPlayController): SudokuSession = composeRule.runOnIdle { controller.board!!.session }

    private fun cell(idx: Int) = composeRule.onNodeWithTag("sudoku.cell.$idx")

    /** A digit key, in either mode: its label is "<d>, <n> left" or "Note <d>, <n> left". */
    private fun key(digit: Int): SemanticsNodeInteraction = composeRule.onNode(
        SemanticsMatcher("digit key $digit") { node ->
            node.config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }
                .any { Regex("^(Note )?$digit, \\d+ left$").matches(it) }
        },
    )

    private fun hint() = composeRule.onNode(hasClickAction() and hasText("Hint", substring = true))

    @Test
    fun `the tour plays 450 from first entry to the solved hand-off`() {
        val controller = adopted()
        setScreen(controller, PlayLayout.SinglePane)

        cell(0).performClick().assertIsSelected()
        key(4).performClick()
        assertEquals(SudokuCell.Entry(4), session(controller).board.cells[0])

        composeRule.onNodeWithText("Notes").performClick()
        cell(1).performClick()
        key(1).performClick()
        assertEquals(SudokuCell.Notes(1), session(controller).board.cells[1])

        composeRule.onNodeWithText("Erase").performClick()
        assertEquals(SudokuCell.Empty, session(controller).board.cells[1])

        composeRule.onNodeWithTag("play.undo").performClick()
        assertEquals(SudokuCell.Notes(1), session(controller).board.cells[1])

        val beforeHints = session(controller).board
        hint().performClick()
        assertEquals(1, session(controller).hintsUsed)
        assertEquals(beforeHints, session(controller).board)
        hint().performClick()
        assertEquals(1, session(controller).hintsUsed)
        assertNotEquals(beforeHints, session(controller).board)

        repeat(givens.size * 2) { if (!session(controller).isComplete) hint().performClick() }

        val won = session(controller)
        assertTrue(won.isComplete)
        assertEquals(solution, won.board.cells.map { it.digit })
        composeRule.runOnIdle { assertEquals(listOf(won.elapsedMs to won.hintsUsed), solved) }
        hint().assertIsNotEnabled()
        composeRule.onNodeWithText("Notes").assertIsNotEnabled()
        composeRule.onNodeWithText("Erase").assertIsNotEnabled()
        // Clear is never disabled; on a solved board it is a no-op (the win is final).
        composeRule.onNodeWithTag("sudoku.clear").assertIsEnabled().performClick()
        assertEquals(won, session(controller))
        composeRule.runOnIdle { assertEquals(null, controller.board!!.selected) }
        cell(0).performClick().assertIsNotSelected()
    }

    @Test
    fun `single pane stacks the keypad under the grid in the board pane`() {
        setScreen(adopted(), PlayLayout.SinglePane)

        composeRule.onNode(hasTestTag("sudoku.keypad") and hasAnyAncestor(hasTestTag("sudoku.board_area"))).assertExists()
        composeRule.onNode(hasTestTag("sudoku.keypad") and hasAnyAncestor(hasTestTag("play.side_panel"))).assertDoesNotExist()
        composeRule.onNode(hasTestTag("sudoku.clear") and hasAnyAncestor(hasTestTag("sudoku.keypad"))).assertExists()
        composeRule.onNode(hasTestTag("sudoku.clear") and hasAnyAncestor(hasTestTag("play.side_panel"))).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun `two pane - board with the digit pad and its notes column under a toolbar with erase and clear`() {
        val controller = adopted()
        setScreen(controller, PlayLayout.TwoPane)

        val inBoard = hasAnyAncestor(hasTestTag("sudoku.board_area"))
        val inToolbar = hasAnyAncestor(hasTestTag("play.toolbar"))
        composeRule.onNode(hasTestTag("sudoku.keypad") and inBoard).assertExists()
        composeRule.onNode(hasTestTag("sudoku.notes_key") and inBoard).assertExists()
        composeRule.onNode(hasText("Notes") and hasClickAction()).assertDoesNotExist()
        for (tag in listOf("play.undo", "play.redo", "sudoku.clear")) composeRule.onNode(hasTestTag(tag) and inToolbar).assertExists()
        composeRule.onNode(hasText("Erase") and inToolbar).assertExists()
        composeRule.onNode(hasClickAction() and hasText("Hint", substring = true) and inToolbar).assertExists()
        composeRule.onNode(hasTestTag("play.undo") and hasAnyAncestor(hasTestTag("play.side_pane"))).assertDoesNotExist()

        // Two rows of three digits, the notes key a fourth column as tall as both; the
        // toolbar sits over the grid (A15).
        val keys = (1..6).map { key(it).getUnclippedBoundsInRoot() }
        val notes = composeRule.onNodeWithTag("sudoku.notes_key").getUnclippedBoundsInRoot()
        assertEquals(listOf(keys[0].top, keys[3].top), keys.map { it.top }.distinct())
        assertEquals(keys[0].top, notes.top)
        assertEquals(keys[3].bottom, notes.bottom)
        assertTrue("$notes after ${keys[2]}", notes.left > keys[2].right)
        val grid = composeRule.onNodeWithTag("sudoku.grid").getUnclippedBoundsInRoot()
        val toolbar = composeRule.onNodeWithTag("play.toolbar").getUnclippedBoundsInRoot()
        assertTrue("$toolbar above $grid", toolbar.bottom <= grid.top)
        val buttons = listOf("play.undo", "sudoku.clear").map { composeRule.onNodeWithTag(it).getUnclippedBoundsInRoot() }
        assertEquals(grid.left.value, buttons[0].left.value, 1f)
        assertEquals(grid.right.value, buttons[1].right.value, 1f)

        composeRule.onNodeWithTag("sudoku.notes_key").performClick()
        cell(1).performClick()
        key(1).performClick()
        assertEquals(SudokuCell.Notes(1), session(controller).board.cells[1])
        composeRule.onNode(hasText("Erase") and inToolbar).performClick()
        assertEquals(SudokuCell.Empty, session(controller).board.cells[1])
        composeRule.onNodeWithTag("play.undo").performClick()
        assertEquals(SudokuCell.Notes(1), session(controller).board.cells[1])
        composeRule.onNodeWithTag("sudoku.clear").performClick()
        assertEquals(SudokuCell.Empty, session(controller).board.cells[1])
    }

    @Test
    fun `clear on an empty board stays enabled and changes nothing`() {
        val controller = adopted()
        setScreen(controller, PlayLayout.SinglePane)
        val before = session(controller)

        composeRule.onNodeWithTag("sudoku.clear").assertIsEnabled().performClick()

        assertEquals(before, session(controller))
        assertFalse(session(controller).history.canUndo)
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun `two pane toolbar clear is enabled on an empty board and a tap changes nothing`() {
        val controller = adopted()
        setScreen(controller, PlayLayout.TwoPane)
        val before = session(controller)

        composeRule.onNodeWithTag("sudoku.clear").assertIsEnabled().performClick()

        assertEquals(before, session(controller))
    }

    @Test
    fun `clear wipes the board in one undoable step`() {
        val controller = adopted()
        setScreen(controller, PlayLayout.SinglePane)
        val clear = composeRule.onNodeWithTag("sudoku.clear")
        clear.assertIsEnabled()

        cell(0).performClick()
        key(4).performClick()
        cell(1).performClick()
        key(6).performClick()
        clear.assertIsEnabled().performClick()

        assertEquals(SudokuSession(ref = ref, puzzle = peanut450).board, session(controller).board)
        composeRule.onNodeWithTag("play.undo").performClick()
        assertEquals(listOf(4, 6), session(controller).board.cells.take(2).map { it.digit })
    }

    @Test
    fun `the tutorial board carries the keypad`() {
        val controller = adopted()
        composeRule.setContent {
            PuzzleBoxTheme { ui.Board(controller, Modifier) }
        }

        composeRule.onNodeWithTag("sudoku.grid").assertExists()
        composeRule.onNodeWithTag("sudoku.keypad").assertExists()
    }
}
