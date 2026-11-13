package com.ivandsky.puzzlebox.games.queensui

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
import com.ivandsky.puzzlebox.games.queens.Cell
import com.ivandsky.puzzlebox.games.queens.QueensReplay
import com.ivandsky.puzzlebox.games.queens.QueensReplayMove
import com.ivandsky.puzzlebox.games.queens.QueensSession
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
class QueensReplayBoardGoldenCompactTest : QueensReplayBoardGoldenBase("compact", 320)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h900dp-420dpi")
class QueensReplayBoardGoldenExpandedTest : QueensReplayBoardGoldenBase("expanded", 480)

abstract class QueensReplayBoardGoldenBase(private val widthLabel: String, private val sideDp: Int) {

    @get:Rule
    val composeRule = createComposeRule()

    // Queens 1 and 2 share a row (a clash shows in the middle frame); the end frame is the solution.
    private val script = listOf(
        QueensReplayMove.Place(Cell(1)),
        QueensReplayMove.Place(Cell(2)),
        QueensReplayMove.Cross(Cell(15)),
        QueensReplayMove.Remove(Cell(2)),
        QueensReplayMove.Place(Cell(7)),
        QueensReplayMove.Place(Cell(8)),
        QueensReplayMove.Place(Cell(14)),
    )

    private fun sessionAfter(moves: Int): QueensSession {
        var session = QueensUiFixtures.session()
        script.take(moves).forEachIndexed { i, move ->
            session = checkNotNull(QueensReplay.step(session, move.toReplayMove(), i * 1_000L + 1_000L)) { "script move $i" }
        }
        return session
    }

    private fun frameAfter(moves: Int): ReplayFrame {
        val initial = QueensUiFixtures.session()
        val session = sessionAfter(moves)
        val atMs = moves * 1_000L
        return ReplayFrame(
            session = session,
            before = if (moves == 0) null else sessionAfter(moves - 1),
            hinted = emptySet(),
            facts = QueensReplay.factsOf(initial)(session),
            atMs = atMs,
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
                    Box { QueensReplayBoard.Board(frame, ReplayBoardOptions(animate = false, showErrors = true), Modifier.size(sideDp.dp)) }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(out(name, dark))
    }

    @Test fun `start light`() = capture("start", false, 0)
    @Test fun `start dark`() = capture("start", true, 0)
    @Test fun `middle light`() = capture("middle", false, 3)
    @Test fun `middle dark`() = capture("middle", true, 3)
    @Test fun `end light`() = capture("end", false, script.size)
    @Test fun `end dark`() = capture("end", true, script.size)
}
