package com.ivandsky.puzzlebox.games.zipui

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
import com.ivandsky.puzzlebox.games.zip.Cell
import com.ivandsky.puzzlebox.games.zip.ZipReplay
import com.ivandsky.puzzlebox.games.zip.ZipReplayMove
import com.ivandsky.puzzlebox.games.zip.ZipSession
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
class ZipReplayBoardGoldenCompactTest : ZipReplayBoardGoldenBase("compact", 320)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h900dp-420dpi")
class ZipReplayBoardGoldenExpandedTest : ZipReplayBoardGoldenBase("expanded", 480)

abstract class ZipReplayBoardGoldenBase(private val widthLabel: String, private val sideDp: Int) {

    @get:Rule
    val composeRule = createComposeRule()

    // The solve walks past the first turn, backs off to cell 9 and resumes; the end frame is the full path.
    private val script: List<ZipReplayMove> =
        listOf(0, 1, 2, 3, 4, 9, 8).map { ZipReplayMove.Extend(Cell(it)) } +
            ZipReplayMove.RetractTo(Cell(9)) +
            listOf(8, 7, 6, 5, 10, 11, 12, 13, 14, 19, 18, 17, 16, 15, 20, 21, 22, 23, 24)
                .map { ZipReplayMove.Extend(Cell(it)) }

    private fun sessionAfter(moves: Int): ZipSession {
        var session = ZipPlayFixture.session()
        script.take(moves).forEachIndexed { i, move ->
            session = checkNotNull(ZipReplay.step(session, move.toReplayMove(), i * 1_000L + 1_000L)) { "script move $i" }
        }
        return session
    }

    private fun frameAfter(moves: Int): ReplayFrame {
        val initial = ZipPlayFixture.session()
        val session = sessionAfter(moves)
        return ReplayFrame(
            session = session,
            before = if (moves == 0) null else sessionAfter(moves - 1),
            moves = emptyList(),
            hinted = emptySet(),
            facts = ZipReplay.factsOf(initial)(session),
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
                    Box { ZipReplayBoard.Board(frame, ReplayBoardOptions(animate = false, showErrors = true), Modifier.size(sideDp.dp)) }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(out(name, dark))
    }

    @Test fun `start light`() = capture("start", false, 0)
    @Test fun `start dark`() = capture("start", true, 0)
    @Test fun `middle light`() = capture("middle", false, 9)
    @Test fun `middle dark`() = capture("middle", true, 9)
    @Test fun `end light`() = capture("end", false, script.size)
    @Test fun `end dark`() = capture("end", true, script.size)
}
