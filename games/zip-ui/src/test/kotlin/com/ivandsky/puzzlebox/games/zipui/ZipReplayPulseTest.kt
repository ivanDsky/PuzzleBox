package com.ivandsky.puzzlebox.games.zipui

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.replay.ReplayFrame
import com.ivandsky.puzzlebox.gameui.replay.ReplayBoardOptions
import com.ivandsky.puzzlebox.games.zip.ZipReplay
import com.ivandsky.puzzlebox.games.zip.ZipReplayMove
import com.ivandsky.puzzlebox.games.zip.ZipSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Zip replay board: the next number to reach ripples a halo, as on the live board, whether the replay plays or is
 * paused; Reduce motion leaves it still. A puzzle nobody has played (its givens thumbnail) marks nothing. The start cell is traced,
 * so the next number is "2" in the middle cell (row 2, column 2).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class ZipReplayPulseTest {

    @get:Rule
    val rule = createComposeRule()

    private fun frame(): ReplayFrame {
        val initial = ZipPlayFixture.session()
        val session: ZipSession = checkNotNull(
            ZipReplay.step(initial, ZipReplayMove.Extend(ZipPlayFixture.solution.first()).toReplayMove(), 1_000L),
        )
        return ReplayFrame(
            session = session,
            before = null,
            moves = emptyList(),
            hinted = emptySet(),
            facts = ZipReplay.factsOf(initial)(session),
            atMs = 1_000L,
            sinceMoveMs = 0L,
            ended = false,
        )
    }

    private fun show(animate: Boolean = false, reducedMotion: Boolean = false) {
        if (reducedMotion) {
            val context = ApplicationProvider.getApplicationContext<Context>()
            Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        }
        rule.mainClock.autoAdvance = false
        rule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Box {
                    ZipReplayBoard.Board(
                        frame(),
                        ReplayBoardOptions(animate = animate, showErrors = true),
                        Modifier.size(320.dp).testTag(BOARD),
                    )
                }
            }
        }
    }

    /** Every pixel of the next number's cell, every 100 ms over a little more than one pulse cycle. */
    private fun cellOverACycle(): List<List<Int>> = List(14) {
        rule.mainClock.advanceTimeBy(100)
        val pixels = rule.onNodeWithTag(BOARD).captureToImage().toPixelMap()
        val pitch = pixels.width / 5
        (2 * pitch until 3 * pitch).flatMap { y -> (2 * pitch until 3 * pitch).map { x -> pixels[x, y].toArgb() } }
    }

    @Test
    fun `the replay's next number pulses while paused`() = pulses(animate = false)

    @Test
    fun `the replay's next number pulses while playing`() = pulses(animate = true)

    private fun pulses(animate: Boolean) {
        show(animate = animate)
        val seen = cellOverACycle()
        assertTrue("the halo should ripple, saw ${seen.distinct().size} looks", seen.distinct().size > 3)
    }

    @Test
    fun `the replay's next number is still with Reduce motion`() {
        show(reducedMotion = true)

        assertEquals(1, cellOverACycle().distinct().size)
    }

    @Test
    fun `a puzzle nobody has played marks no next number, though the first is still to reach`() {
        val board = checkNotNull(ZipReplayBoard.givens(ZipPlayFixture.envelope()))
        rule.mainClock.autoAdvance = false
        rule.setContent {
            PuzzleBoxTheme(darkTheme = false) { Box { board.content(Modifier.size(320.dp).testTag(BOARD)) } }
        }
        rule.mainClock.advanceTimeBy(100)

        rule.onNodeWithTag(BOARD).assertExists()
        rule.onNodeWithTag("play.next_number_halo").assertDoesNotExist()
        rule.onNodeWithTag("play.next_number_ring").assertDoesNotExist()
    }

    private companion object {
        const val BOARD = "replay.board"
    }
}
