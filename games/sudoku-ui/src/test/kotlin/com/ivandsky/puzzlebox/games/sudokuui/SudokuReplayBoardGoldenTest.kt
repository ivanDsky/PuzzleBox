package com.ivandsky.puzzlebox.games.sudokuui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
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
import java.io.File

/** Replay-board goldens at start / middle / end of one scripted solve, built through the adapter. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class SudokuReplayBoardGoldenCompactTest : SudokuReplayBoardGoldenBase("compact", 320)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h900dp-420dpi")
class SudokuReplayBoardGoldenExpandedTest : SudokuReplayBoardGoldenBase("expanded", 480)

abstract class SudokuReplayBoardGoldenBase(private val widthLabel: String, private val sideDp: Int) {

    @get:Rule
    val composeRule = createComposeRule()

    // #450 "Peanut" (spec A2).
    private val givens = grid("..3.5. .2.4.6 1..... .....2 2.4.1. .1.3..")
    private val solution = grid("463251 521436 142563 356142 234615 615324")
    private val puzzle = SudokuPuzzle(SudokuGames.MINI.variant, givens, solution)

    private fun grid(text: String): List<Int> =
        text.filterNot { it.isWhitespace() }.map { if (it == '.') 0 else it.digitToInt() }

    // The first five moves leave right digits, a notes cell, and a 2 clashing with the given 2 in its row and box
    // (the middle frame); the rest clears the clash, takes a revealed hint cell and fills every other cell.
    private val opening = listOf(
        SudokuReplayMove.Place(0, 4),
        SudokuReplayMove.Place(1, 6),
        SudokuReplayMove.ToggleNote(3, 2),
        SudokuReplayMove.ToggleNote(3, 6),
        SudokuReplayMove.Place(6, 2),
    )
    private val script: List<SudokuReplayMove> = buildList {
        addAll(opening)
        add(SudokuReplayMove.Erase(6))
        add(SudokuReplayMove.Hint)
        add(SudokuReplayMove.HintReveal(13, solution[13]))
        val done = setOf(0, 1, 13)
        givens.indices.filter { givens[it] == 0 && it !in done }.forEach { add(SudokuReplayMove.Place(it, solution[it])) }
    }
    private val middleMoves = opening.size

    private fun initial() = SudokuSession(ref = null, puzzle = puzzle)

    private fun sessionAfter(moves: Int): SudokuSession {
        var session = initial()
        script.take(moves).forEachIndexed { i, move ->
            session = checkNotNull(SudokuReplay.step(session, move.toReplayMove(), i * 1_000L + 1_000L)) { "script move $i" }
        }
        return session
    }

    private fun frameAfter(moves: Int): ReplayFrame {
        val session = sessionAfter(moves)
        return ReplayFrame(
            session = session,
            before = if (moves == 0) null else sessionAfter(moves - 1),
            hinted = emptySet(),
            facts = SudokuReplay.factsOf(initial())(session),
            atMs = moves * 1_000L,
            sinceMoveMs = 0L,
            ended = moves == script.size,
        )
    }

    private fun out(name: String, dark: Boolean): String =
        File(checkNotNull(System.getProperty("user.dir")))
            .resolve("snapshots/replay-$name-$widthLabel-${if (dark) "dark" else "light"}.png")
            .canonicalFile
            .also { checkNotNull(it.parentFile).mkdirs() }
            .absolutePath

    private fun capture(name: String, dark: Boolean, moves: Int) {
        val frame = frameAfter(moves)
        composeRule.setContent {
            // currentIsDarkTheme() reads uiMode, so the capture sets it as a device would.
            val config = android.content.res.Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) android.content.res.Configuration.UI_MODE_NIGHT_YES
                    else android.content.res.Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config) {
                PuzzleBoxTheme(darkTheme = dark) {
                    Box { SudokuReplayBoard.Board(frame, ReplayBoardOptions(animate = false, showErrors = true), Modifier.size(sideDp.dp)) }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(out(name, dark))
    }

    @Test fun `end frame is the solved board`() {
        check(frameAfter(script.size).facts.solved)
    }

    @Test fun `start light`() = capture("start", false, 0)
    @Test fun `start dark`() = capture("start", true, 0)
    @Test fun `middle light`() = capture("middle", false, middleMoves)
    @Test fun `middle dark`() = capture("middle", true, middleMoves)
    @Test fun `end light`() = capture("end", false, script.size)
    @Test fun `end dark`() = capture("end", true, script.size)
}
