package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescriptionExactly
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuSession
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class SudokuGridTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun grid(text: String): List<Int> = text.filterNot { it.isWhitespace() }.map { if (it == '.') 0 else it.digitToInt() }

    private val mini = SudokuSession(
        ref = null,
        puzzle = SudokuPuzzle(
            SudokuGames.MINI.variant,
            grid("..3.5. .2.4.6 1..... .....2 2.4.1. .1.3.."),
            grid("463251 521436 142563 356142 234615 615324"),
        ),
    )

    /** Row 1: entry 4 at column 1 (a duplicate of nothing), notes 1 2 5 at column 2, a conflicting 3 at column 4. */
    private val played = mini.copy(
        board = mini.board
            .with(0, SudokuCell.Entry(4))
            .with(1, SudokuCell.Notes(0b10011))
            .with(3, SudokuCell.Entry(3)),
    )

    private val selections = mutableListOf<Int>()

    private fun show(session: SudokuSession, selected: Int? = null) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                SudokuGrid(
                    model = sudokuBoardModel(session, selected),
                    onSelect = { selections += it },
                    modifier = Modifier.size(360.dp),
                )
            }
        }
    }

    private fun cell(idx: Int) = composeRule.onNodeWithTag("sudoku.cell.$idx")

    @Test
    fun `each cell reads as one merged label`() {
        show(played)

        cell(2).assert(hasContentDescriptionExactly("Row 1, column 3, given 3, conflict"))
        cell(0).assert(hasContentDescriptionExactly("Row 1, column 1, 4"))
        cell(1).assert(hasContentDescriptionExactly("Row 1, column 2, notes 1 2 5"))
        cell(3).assert(hasContentDescriptionExactly("Row 1, column 4, 3, conflict"))
        cell(35).assert(hasContentDescriptionExactly("Row 6, column 6, empty"))
        cell(1).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
    }

    @Test
    fun `the grid carries collection info and each cell its position`() {
        show(mini)

        composeRule.onNodeWithTag("sudoku.grid").assert(
            SemanticsMatcher("6 x 6 collection") {
                val info = it.config[SemanticsProperties.CollectionInfo]
                info.rowCount == 6 && info.columnCount == 6
            },
        )
        cell(14).assert(
            SemanticsMatcher("row 2, column 2") {
                val info = it.config[SemanticsProperties.CollectionItemInfo]
                info.rowIndex == 2 && info.columnIndex == 2
            },
        )
    }

    @Test
    fun `tapping a cell selects it`() {
        show(mini)

        cell(14).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(14), selections)
    }

    @Test
    fun `the click action selects the cell for TalkBack`() {
        show(mini, selected = 0)

        cell(20).performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(listOf(20), selections)
        cell(0).assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
    }
}
