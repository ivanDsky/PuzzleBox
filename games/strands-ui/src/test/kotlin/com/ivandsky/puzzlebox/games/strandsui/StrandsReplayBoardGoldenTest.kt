package com.ivandsky.puzzlebox.games.strandsui

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
import com.ivandsky.puzzlebox.game.DecodeResult
import com.ivandsky.puzzlebox.game.replay.ReplayFrame
import com.ivandsky.puzzlebox.gameui.replay.ReplayBoardOptions
import com.ivandsky.puzzlebox.games.strands.HintStage
import com.ivandsky.puzzlebox.games.strands.StrandsPuzzleRef
import com.ivandsky.puzzlebox.games.strands.StrandsReplay
import com.ivandsky.puzzlebox.games.strands.StrandsReplayMove
import com.ivandsky.puzzlebox.games.strands.StrandsSession
import com.ivandsky.puzzlebox.games.strands.StrandsState
import com.ivandsky.puzzlebox.games.strands.codec.StrandsCodec
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Replay-board goldens at start / middle / end of one scripted solve of the real 2026-11-06 day, built through the adapter. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class StrandsReplayBoardGoldenCompactTest : StrandsReplayBoardGoldenBase("compact", 320)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h900dp-420dpi")
class StrandsReplayBoardGoldenExpandedTest : StrandsReplayBoardGoldenBase("expanded", 480)

abstract class StrandsReplayBoardGoldenBase(private val widthLabel: String, private val sideDp: Int) {

    @get:Rule
    val composeRule = createComposeRule()

    private val initial: StrandsSession = run {
        val fetched = RealDay.fetched
        val puzzle = (StrandsCodec.decode(fetched.puzzle.payload) as DecodeResult.Ok).value
        StrandsSession(ref = fetched.ref as StrandsPuzzleRef, state = StrandsState(puzzle))
    }
    private val cols = initial.state.puzzle.cols

    // Theme words come in feed order (ACORN, BRANCH, …); the spangram is index 7. The middle frame
    // (3 moves) holds a found word and BRANCH's revealed order; the end frame is the solved board.
    private val script = listOf(
        StrandsReplayMove.Submit(RealDay.themeWords[0]),
        StrandsReplayMove.Hint(1, HintStage.Outlined),
        StrandsReplayMove.Hint(1, HintStage.OrderRevealed),
        StrandsReplayMove.Submit(RealDay.themeWords[1]),
        StrandsReplayMove.Submit(RealDay.spangram),
    ) + RealDay.themeWords.drop(2).map { StrandsReplayMove.Submit(it) }

    private fun sessionAfter(moves: Int): StrandsSession {
        var session = initial
        script.take(moves).forEachIndexed { i, move ->
            session = checkNotNull(StrandsReplay.step(session, move.toReplayMove(cols), i * 1_000L + 1_000L)) { "script move $i" }
        }
        return session
    }

    private fun frameAfter(moves: Int): ReplayFrame {
        val session = sessionAfter(moves)
        return ReplayFrame(
            session = session,
            before = if (moves == 0) null else sessionAfter(moves - 1),
            hinted = emptySet(),
            facts = StrandsReplay.factsOf(initial)(session),
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
                    Box { StrandsReplayBoard.Board(frame, ReplayBoardOptions(animate = false, showErrors = true), Modifier.size(sideDp.dp)) }
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
