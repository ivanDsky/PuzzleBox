package com.ivandsky.puzzlebox.games.tangoui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.ivandsky.puzzlebox.designsystem.MotionTokens
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.replay.ReplayFrame
import com.ivandsky.puzzlebox.gameui.replay.ReplayBoardOptions
import com.ivandsky.puzzlebox.games.tango.TangoCell
import com.ivandsky.puzzlebox.games.tango.TangoReplay
import com.ivandsky.puzzlebox.games.tango.TangoReplayMove
import com.ivandsky.puzzlebox.games.tango.TangoSession
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Replay-board goldens at start / middle / end of one scripted solve, built through the adapter. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class TangoReplayBoardGoldenCompactTest : TangoReplayBoardGoldenBase("compact", 320)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h900dp-420dpi")
class TangoReplayBoardGoldenExpandedTest : TangoReplayBoardGoldenBase("expanded", 480)

abstract class TangoReplayBoardGoldenBase(private val widthLabel: String, private val sideDp: Int) {

    @get:Rule
    val composeRule = createComposeRule()

    // A sun at 2 makes three suns in row 0 (the middle frame, settled, shows it); the end frame is the solution.
    private val script: List<TangoReplayMove> = buildList {
        add(TangoReplayMove.Place(2, TangoCell.SUN))
        add(TangoReplayMove.Place(8, TangoCell.MOON))
        add(TangoReplayMove.Place(3, TangoCell.SUN))
        add(TangoReplayMove.Place(2, TangoCell.MOON))
        val placed = setOf(2, 3, 8)
        TangoPlayFixture.placements.filter { (idx, _) -> idx !in placed }
            .forEach { (idx, value) -> add(TangoReplayMove.Place(idx, value)) }
    }

    private val initial = TangoSession(ref = null, puzzle = TangoPlayFixture.puzzle)

    private fun sessionAfter(moves: Int): TangoSession {
        var session = initial
        script.take(moves).forEachIndexed { i, move ->
            session = checkNotNull(TangoReplay.step(session, move.toReplayMove(), i * 1_000L + 1_000L)) { "script move $i" }
        }
        return session
    }

    // The settle window has passed, so violations show: the golden pins the settled board.
    private fun frameAfter(moves: Int): ReplayFrame {
        val session = sessionAfter(moves)
        return ReplayFrame(
            session = session,
            before = if (moves == 0) null else sessionAfter(moves - 1),
            hinted = emptySet(),
            facts = TangoReplay.factsOf(initial)(session),
            atMs = moves * 1_000L,
            sinceMoveMs = MotionTokens.SettleMillis,
            ended = moves == script.size,
        )
    }

    private fun goldenPath(name: String, dark: Boolean) =
        File("src/test/resources/goldens/replay-$name-$widthLabel-${if (dark) "dark" else "light"}.png")

    // Cell 8 holds a moon, so the hint on the next board proves cell 2 is a moon: the badge sits on cell 2.
    private fun hintFrame(): ReplayFrame {
        val before = checkNotNull(TangoReplay.step(initial, TangoReplayMove.Place(8, TangoCell.MOON).toReplayMove(), 1_000L))
        val hint = TangoReplayMove.Hint.toReplayMove()
        val after = checkNotNull(TangoReplay.step(before, hint, 2_000L))
        return ReplayFrame(
            session = after,
            before = before,
            hinted = TangoReplay.markOf(before, hint, after, null).hinted,
            facts = TangoReplay.factsOf(initial)(after),
            atMs = 2_000L,
            sinceMoveMs = MotionTokens.SettleMillis,
            ended = false,
        )
    }

    private fun capture(name: String, dark: Boolean, moves: Int) = capture(name, dark, frameAfter(moves))

    private fun capture(name: String, dark: Boolean, frame: ReplayFrame) {
        composeRule.setContent {
            // currentIsDarkTheme() reads uiMode, so the capture sets it as a device would.
            val config = android.content.res.Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (dark) android.content.res.Configuration.UI_MODE_NIGHT_YES
                    else android.content.res.Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(LocalConfiguration provides config) {
                PuzzleBoxTheme(darkTheme = dark) {
                    Box { TangoReplayBoard.Board(frame, ReplayBoardOptions(animate = false, showErrors = true), Modifier.size(sideDp.dp)) }
                }
            }
        }
        composeRule.waitForIdle()
        val goldenName = goldenPath(name, dark).name
        val scratch = File("build/roborecord/$goldenName").apply { parentFile?.mkdirs() }
        composeRule.onRoot().captureRoboImage(scratch)
        GoldenCompare.check(goldenPath(name, dark), scratch, goldenName)
    }

    @Test fun `start light`() = capture("start", false, 0)
    @Test fun `start dark`() = capture("start", true, 0)
    @Test fun `middle light`() = capture("middle", false, 3)
    @Test fun `middle dark`() = capture("middle", true, 3)
    @Test fun `end light`() = capture("end", false, script.size)
    @Test fun `end dark`() = capture("end", true, script.size)
    @Test fun `hint light`() = capture("hint", false, hintFrame())
    @Test fun `hint dark`() = capture("hint", true, hintFrame())
}
