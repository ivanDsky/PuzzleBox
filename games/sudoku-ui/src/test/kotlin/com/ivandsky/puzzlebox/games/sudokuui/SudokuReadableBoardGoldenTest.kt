package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.games.sudoku.SudokuCell
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuSamples
import com.ivandsky.puzzlebox.games.sudoku.SudokuSession
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Goldens of the grid on the readable board, light and dark: the look the approved board token
 * sheets set. The grid alone on the screen background, so the lines, the frame and the digit inks are all there is to
 * compare.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class SudokuReadableBoardGoldenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun grid(text: String): List<Int> = text.filterNot { it.isWhitespace() }.map { if (it == '.') 0 else it.digitToInt() }

    // #450 "Peanut", the fixture of the play-screen goldens.
    private val mini = SudokuSession(
        ref = null,
        puzzle = SudokuPuzzle(
            SudokuGames.MINI.variant,
            grid("..3.5. .2.4.6 1..... .....2 2.4.1. .1.3.."),
            grid("463251 521436 142563 356142 234615 615324"),
        ),
    )

    /** A right 4 (selected), a notes cell, and a 2 clashing with the given 2 in its row and box. */
    private val miniMidPlay = mini.copy(
        board = mini.board
            .with(0, SudokuCell.Entry(4))
            .with(3, SudokuCell.Notes(0b100010))
            .with(6, SudokuCell.Entry(2))
            .with(13, SudokuCell.Entry(4)),
    )

    private val classic = SudokuSession(ref = null, puzzle = SudokuSamples.classic)

    @Test fun `6x6 start light`() = capture("start-6x6", dark = false, mini)
    @Test fun `6x6 start dark`() = capture("start-6x6", dark = true, mini)

    @Test fun `6x6 mid play light`() = capture("mid-6x6", dark = false, miniMidPlay, selected = 0)
    @Test fun `6x6 mid play dark`() = capture("mid-6x6", dark = true, miniMidPlay, selected = 0)

    /** The selected tile with flat surfaces on (nav-rethink M10-8): no lip under it. */
    @Test fun `6x6 mid play flat light`() = capture("mid-6x6-flat", dark = false, miniMidPlay, selected = 0, flat = true)
    @Test fun `6x6 mid play flat dark`() = capture("mid-6x6-flat", dark = true, miniMidPlay, selected = 0, flat = true)

    @Test fun `9x9 start light`() = capture("start-9x9", dark = false, classic)
    @Test fun `9x9 start dark`() = capture("start-9x9", dark = true, classic)

    private fun capture(name: String, dark: Boolean, session: SudokuSession, selected: Int? = null, flat: Boolean = false) {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = dark, flatSurfaces = flat) {
                Box(Modifier.testTag(SCENE_TAG).background(MaterialTheme.colorScheme.background).padding(16.dp)) {
                    SudokuGrid(sudokuBoardModel(session, selected), onSelect = null, modifier = Modifier.size(360.dp))
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(SCENE_TAG).captureRoboImage(out(name, dark))
    }

    private fun out(name: String, dark: Boolean): String =
        File(checkNotNull(System.getProperty("user.dir")))
            .resolve("snapshots/readable-$name-${if (dark) "dark" else "light"}.png")
            .canonicalFile
            .also { checkNotNull(it.parentFile).mkdirs() }
            .absolutePath

    private companion object {
        const val SCENE_TAG = "sudoku.readable.scene"
    }
}
