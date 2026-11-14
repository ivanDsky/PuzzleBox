package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.replay.ReplayFrame
import com.ivandsky.puzzlebox.gameui.replay.ReplayBoardOptions
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuReplay
import com.ivandsky.puzzlebox.games.sudoku.SudokuReplayMove
import com.ivandsky.puzzlebox.games.sudoku.SudokuSession
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasContentDescription
import org.junit.Assert.assertTrue

/** The Sudoku replay board marks a conflicting digit only while [ReplayBoardOptions.showErrors] (spec S18 / A27). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class SudokuReplayBoardShowErrorsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun grid(text: String): List<Int> =
        text.filterNot { it.isWhitespace() }.map { if (it == '.') 0 else it.digitToInt() }

    // Mini #450: a 2 at cell 6 clashes with the given 2 in its row and box.
    private val puzzle = SudokuPuzzle(
        SudokuGames.MINI.variant,
        grid("..3.5. .2.4.6 1..... .....2 2.4.1. .1.3.."),
        grid("463251 521436 142563 356142 234615 615324"),
    )
    private val frame: ReplayFrame = run {
        val initial = SudokuSession(ref = null, puzzle = puzzle)
        val session = checkNotNull(SudokuReplay.step(initial, SudokuReplayMove.Place(6, 2).toReplayMove(), 1_000L))
        ReplayFrame(session, initial, emptyList(), emptySet(), SudokuReplay.factsOf(initial)(session), 1_000L, 0L, false)
    }

    private fun show(showErrors: Boolean) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                SudokuReplayBoard.Board(frame, ReplayBoardOptions(animate = false, showErrors = showErrors), Modifier.size(320.dp))
            }
        }
        composeRule.waitForIdle()
    }

    @Test fun `errors on mark the clashing cells`() {
        show(showErrors = true)
        assertTrue(composeRule.onAllNodes(hasContentDescription("conflict", substring = true)).fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun `errors off mark no cell`() {
        show(showErrors = false)
        composeRule.onAllNodes(hasContentDescription("conflict", substring = true)).assertCountEquals(0)
    }
}
