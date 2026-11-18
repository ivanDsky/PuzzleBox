package com.ivandsky.puzzlebox.games.sudokuui

import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.ivandsky.puzzlebox.games.sudoku.codec.SudokuCodec
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import com.ivandsky.puzzlebox.model.PuzzleKey
import java.io.File
import java.time.Instant
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
 * Goldens of the Mini Sudoku play screen (nav-rethink B5), light and dark: a phone, a portrait
 * tablet and a landscape tablet (the controls in a side column, Erase there), plain and in notes
 * mode (the italic pencil digits). The shared control row (Undo, Redo, Hint, Clear) sits under
 * the keypad; Clear is not in the keypad, whose digits have a strip above them with the corner
 * Notes toggle and Erase. Rendered with `-Proborazzi.record=true` into
 * `games/sudoku-ui/snapshots/` (untracked review captures).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-420dpi")
class SudokuTunedGoldenPhoneTest : SudokuTunedGoldenScenes("tuned-phone")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w800dp-h1280dp-mdpi")
class SudokuTunedGoldenTabletPortraitTest : SudokuTunedGoldenScenes("shipped-tablet-portrait")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
class SudokuTunedGoldenTabletLandscapeTest : SudokuTunedGoldenScenes("shipped-tablet-landscape", PlayLayout.TwoPane)

abstract class SudokuTunedGoldenScenes(
    private val label: String,
    private val layout: PlayLayout = PlayLayout.SinglePane,
) {

    @get:Rule
    val composeRule = createComposeRule()

    private val givens = grid("..3.5. .2.4.6 1..... .....2 2.4.1. .1.3..")
    private val solution = grid("463251 521436 142563 356142 234615 615324")
    private val puzzle = SudokuPuzzle(SudokuGames.MINI.variant, givens, solution)
    private val date = LocalDate.of(2026, 11, 4)

    private fun grid(text: String): List<Int> =
        text.filterNot { it.isWhitespace() }.map { if (it == '.') 0 else it.digitToInt() }

    /** Two right 4s and a selected entry, on a frozen clock. */
    private fun midPlay(): SudokuPlayController {
        val env = PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), {}, {})
        val clock = Instant.parse("2026-11-04T09:00:00Z")
        val controller = SudokuPlayController(SudokuGames.MINI, env, InMemorySudokuHighlightSettings(), now = { clock })
        val envelope = Puzzle(SudokuGames.MINI.id, puzzle.variant.id, SudokuCodec.VERSION, SudokuCodec.encode(puzzle))
        assertEquals(Adopt.Adopted, controller.adoptToday(envelope, SudokuPuzzleRef(SudokuGames.MINI.id, PuzzleKey.Daily(date, 450))))
        val board = checkNotNull(controller.board)
        board.select(0)
        board.input(4)
        board.select(13)
        board.input(4)
        board.select(0)
        return controller
    }

    /** Notes mode on, with pencil marks 2 and 6 in the empty cell beside the 4. */
    private fun notesPlay(): SudokuPlayController {
        val controller = midPlay()
        val board = checkNotNull(controller.board)
        board.select(1)
        board.toggleNotesMode()
        board.input(2)
        board.input(6)
        return controller
    }

    private fun capture(dark: Boolean, notes: Boolean) {
        val ui = SudokuPlayUi(SudokuGames.MINI, InMemorySudokuHighlightSettings())
        val controller = if (notes) notesPlay() else midPlay()
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            val config = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config) {
                PuzzleBoxTheme(darkTheme = dark) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        ui.Screen(
                            controller = controller,
                            status = PlayBoardStatus.Ready,
                            layout = layout,
                            sidePanel = null,
                            onRetry = {},
                            onSolved = { _, _ -> },
                            modifier = Modifier,
                        )
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(out(dark, notes))
    }

    private fun out(dark: Boolean, notes: Boolean): String =
        File(checkNotNull(System.getProperty("user.dir")))
            .resolve("snapshots/${if (notes) "notes" else "mid"}-6x6-$label-${if (dark) "dark" else "light"}.png")
            .canonicalFile
            .also { checkNotNull(it.parentFile).mkdirs() }
            .absolutePath

    @Test fun `mid play light`() = capture(dark = false, notes = false)
    @Test fun `mid play dark`() = capture(dark = true, notes = false)
    @Test fun `notes mode light`() = capture(dark = false, notes = true)
    @Test fun `notes mode dark`() = capture(dark = true, notes = true)

    private companion object {
        const val SETTLE_MS = 2_000L
    }
}
