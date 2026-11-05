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
import com.ivandsky.puzzlebox.games.sudoku.SudokuDefinition
import com.ivandsky.puzzlebox.games.sudoku.SudokuGames
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzle
import com.ivandsky.puzzlebox.games.sudoku.SudokuPuzzleRef
import com.ivandsky.puzzlebox.games.sudoku.SudokuSamples
import com.ivandsky.puzzlebox.games.sudoku.codec.SudokuCodec
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
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
 * Golden screenshots of the Sudoku play screen (S10), light and dark, on a 360x640 phone, a
 * Pixel-class 411x891 phone, and one tablet landscape TwoPane. Recorded by default, verified
 * with `-Proborazzi.verify=true`; goldens live in `games/sudoku-ui/snapshots/`.
 *
 * Every scene is built on the board state before the first composition, so the motion host
 * takes it as the baseline and plays nothing; the paused main clock then steps past any
 * remaining transition, so each capture is a settled frame.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class SudokuGoldenSmallPhoneTest : SudokuGoldenPhoneScenes("compact")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class SudokuGoldenPixelPhoneTest : SudokuGoldenPhoneScenes("pixel")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-land-mdpi")
class SudokuGoldenTabletTest : SudokuGoldenScenes("tablet") {
    @Test fun `two pane mid play light`() = capture("mid-6x6-twopane", dark = false, midPlay(), PlayLayout.TwoPane)
    @Test fun `two pane mid play dark`() = capture("mid-6x6-twopane", dark = true, midPlay(), PlayLayout.TwoPane)
}

abstract class SudokuGoldenPhoneScenes(widthLabel: String) : SudokuGoldenScenes(widthLabel) {
    @Test fun `6x6 start light`() = capture("start-6x6", dark = false, mini())
    @Test fun `6x6 start dark`() = capture("start-6x6", dark = true, mini())

    @Test fun `6x6 mid play light`() = capture("mid-6x6", dark = false, midPlay())
    @Test fun `6x6 mid play dark`() = capture("mid-6x6", dark = true, midPlay())

    @Test fun `6x6 notes mode light`() = capture("notes-6x6", dark = false, notesMode())
    @Test fun `6x6 notes mode dark`() = capture("notes-6x6", dark = true, notesMode())

    @Test fun `9x9 start light`() = capture("start-9x9", dark = false, classic(), game = SudokuGames.CLASSIC)
    @Test fun `9x9 start dark`() = capture("start-9x9", dark = true, classic(), game = SudokuGames.CLASSIC)

    @Test fun `6x6 hint stage 1 light`() = capture("hint-6x6", dark = false, hintStageOne())
    @Test fun `6x6 hint stage 1 dark`() = capture("hint-6x6", dark = true, hintStageOne())

    @Test fun `6x6 solved light`() = capture("solved-6x6", dark = false, solved())
    @Test fun `6x6 solved dark`() = capture("solved-6x6", dark = true, solved())
}

abstract class SudokuGoldenScenes(private val widthLabel: String) {

    @get:Rule
    val composeRule = createComposeRule()

    // #450 "Peanut", the LinkedIn fixture (spec A2).
    private val peanutGivens = grid("..3.5. .2.4.6 1..... .....2 2.4.1. .1.3..")
    private val peanutSolution = grid("463251 521436 142563 356142 234615 615324")
    private val peanut450 = SudokuPuzzle(SudokuGames.MINI.variant, peanutGivens, peanutSolution)
    private val date = LocalDate.of(2026, 11, 4)

    // A frozen clock: the chrome's ticker re-reads it, so the shown time never drifts between runs.
    private var clock: Instant = Instant.parse("2026-11-04T09:00:00Z")

    private fun grid(text: String): List<Int> =
        text.filterNot { it.isWhitespace() }.map { if (it == '.') 0 else it.digitToInt() }

    private fun adopt(definition: SudokuDefinition, puzzle: SudokuPuzzle, number: Int?): SudokuPlayController {
        val env = PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), {}, {})
        val controller = SudokuPlayController(definition, env, now = { clock })
        val envelope = Puzzle(definition.id, puzzle.variant.id, SudokuCodec.VERSION, SudokuCodec.encode(puzzle))
        assertEquals(Adopt.Adopted, controller.adoptToday(envelope, SudokuPuzzleRef(definition.id, date, number)))
        return controller
    }

    /** Lets [elapsedSeconds] pass on the frozen clock, so a running game shows a real time. */
    private fun SudokuPlayController.played(elapsedSeconds: Long): SudokuPlayController = apply {
        clock = clock.plusSeconds(elapsedSeconds)
        tick()
    }

    protected fun mini(): SudokuPlayController = adopt(SudokuGames.MINI, peanut450, 450)

    protected fun classic(): SudokuPlayController = adopt(SudokuGames.CLASSIC, SudokuSamples.classic, null)

    /** Two right 4s, a notes cell, a 2 clashing with the given 2 in its row and box, and an entry selected. */
    protected fun midPlay(): SudokuPlayController = mini().apply {
        val board = checkNotNull(board)
        board.select(0)
        board.input(4)
        board.select(13)
        board.input(4)
        board.select(3)
        board.toggleNotesMode()
        board.input(2)
        board.input(6)
        board.toggleNotesMode()
        board.select(6)
        board.input(2)
        board.select(0)
    }.played(elapsedSeconds = 83)

    /** Notes mode on, a selected cell holding notes beside a second notes cell. */
    protected fun notesMode(): SudokuPlayController = mini().apply {
        val board = checkNotNull(board)
        board.toggleNotesMode()
        board.select(1)
        board.input(2)
        board.input(6)
        board.select(0)
        board.input(4)
        board.input(6)
    }.played(elapsedSeconds = 41)

    protected fun hintStageOne(): SudokuPlayController = mini().apply { requestHint() }

    protected fun solved(): SudokuPlayController = mini().apply {
        val board = checkNotNull(board)
        peanutGivens.indices.filter { peanutGivens[it] == 0 }.forEach { idx ->
            board.select(idx)
            board.input(peanutSolution[idx])
            clock = clock.plusSeconds(4)
        }
        check(board.session.isComplete)
    }

    protected fun capture(
        name: String,
        dark: Boolean,
        controller: SudokuPlayController,
        layout: PlayLayout = PlayLayout.SinglePane,
        game: SudokuDefinition = SudokuGames.MINI,
    ) {
        val ui = SudokuPlayUi(game)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            // Dark captures set the night uiMode too, as a device would, so the theme and
            // anything reading the configuration agree.
            val config = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config) {
                PuzzleBoxTheme(darkTheme = dark) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        ui.Screen(
                            controller = controller,
                            status = PlayBoardStatus.Ready(banner = null),
                            layout = layout,
                            onRetry = {},
                            onSolved = { _, _ -> },
                            onHowToPlay = {},
                            modifier = Modifier,
                        )
                    }
                }
            }
        }
        composeRule.mainClock.advanceTimeBy(SETTLE_MS)
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(out(name, dark))
    }

    private fun out(name: String, dark: Boolean): String =
        File(checkNotNull(System.getProperty("user.dir")))
            .resolve("snapshots/$name-$widthLabel-${if (dark) "dark" else "light"}.png")
            .canonicalFile
            .also { checkNotNull(it.parentFile).mkdirs() }
            .absolutePath

    private companion object {
        /** Past the longest one-shot (the 400 ms solve sweep) and any chrome transition. */
        const val SETTLE_MS = 2_000L
    }
}
