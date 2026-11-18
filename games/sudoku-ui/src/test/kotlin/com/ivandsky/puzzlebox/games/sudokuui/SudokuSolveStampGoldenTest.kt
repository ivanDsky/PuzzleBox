package com.ivandsky.puzzlebox.games.sudokuui

import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzleRef
import com.ivandsky.puzzlebox.games.sudoku.SudokuSession
import com.ivandsky.puzzlebox.games.sudoku.codec.SudokuCodec
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.LocalSolveRestOnBoard
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Golden of a Mini Sudoku solve moment with the game's glyph stamped on the grid (nav-rethink 139), light and dark,
 * 1000 ms after the winning move: the stamp has landed with the pop, and the frame line is lapping the grid.
 * Rendered with `-Proborazzi.record=true` (untracked review captures in `snapshots/`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class SudokuSolveStampGoldenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val givens = grid("..3.5. .2.4.6 1..... .....2 2.4.1. .1.3..")
    private val solution = grid("463251 521436 142563 356142 234615 615324")
    private val puzzle = SudokuPuzzle(SudokuGames.MINI.variant, givens, solution)
    private val ref = SudokuPuzzleRef(SudokuGames.MINI.id, PuzzleKey.Daily(LocalDate.of(2026, 11, 4), 450))
    private val openCells = givens.indices.filter { givens[it] == 0 }

    private fun grid(text: String): List<Int> = text.filterNot { it.isWhitespace() }.map { if (it == '.') 0 else it.digitToInt() }

    @Test fun `the stamp has landed, light`() = capture(dark = false, "solve-stamp-light.png")

    @Test fun `the stamp has landed, dark`() = capture(dark = true, "solve-stamp-dark.png")

    private fun place(state: SudokuBoardState, idx: Int) {
        state.select(idx)
        state.input(solution[idx])
    }

    private fun capture(dark: Boolean, file: String) {
        val ui = SudokuPlayUi(SudokuGames.MINI, InMemorySudokuHighlightSettings())
        val controller = ui.createController(PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), {}, {})) as SudokuPlayController
        val envelope = Puzzle(ui.gameId, puzzle.variant.id, SudokuCodec.VERSION, SudokuCodec.encode(puzzle))
        assertEquals(Adopt.Adopted, controller.adoptToday(envelope, ref))
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config, LocalSolveRestOnBoard provides true) {
                PuzzleBoxTheme(darkTheme = dark) {
                    ui.Screen(
                        controller = controller,
                        status = PlayBoardStatus.Ready,
                        layout = PlayLayout.SinglePane,
                        sidePanel = null,
                        onRetry = {},
                        onSolved = { _, _ -> },
                        modifier = Modifier,
                    )
                }
            }
        }
        val state = composeRule.runOnIdle { controller.board!! }
        composeRule.runOnIdle { openCells.dropLast(1).forEach { place(state, it) } }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.runOnIdle { place(state, openCells.last()) }
        // 50 ms steps: once the sweep is over nothing animates, and one big step would skip the clock's wake-ups.
        repeat(1_000 / 50) { composeRule.mainClock.advanceTimeBy(50L) }
        composeRule.onRoot().captureRoboImage(
            File(checkNotNull(System.getProperty("user.dir"))).resolve("snapshots/$file").canonicalFile.absolutePath,
        )
    }
}
