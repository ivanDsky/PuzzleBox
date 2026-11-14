package com.ivandsky.puzzlebox.games.queensui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
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
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import org.junit.Assert.assertTrue

/** The Queens replay board draws conflict stripes only while [ReplayBoardOptions.showErrors] (spec S18 / A27). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-420dpi")
class QueensReplayBoardShowErrorsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun frameOf(moves: List<QueensReplayMove>): ReplayFrame {
        val initial = QueensUiFixtures.session()
        var session = initial
        moves.forEachIndexed { i, move ->
            session = checkNotNull(QueensReplay.step(session, move.toReplayMove(), i * 1_000L + 1_000L))
        }
        return ReplayFrame(session, null, emptyList(), emptySet(), QueensReplay.factsOf(initial)(session), 2_000L, 0L, false)
    }

    private fun pixels(): List<Int> {
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        return List(bitmap.width * bitmap.height) { bitmap.getPixel(it % bitmap.width, it / bitmap.width) }
    }

    private fun shown(frame: ReplayFrame): Pair<List<Int>, List<Int>> {
        var showErrors by mutableStateOf(true)
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Box(Modifier.background(MaterialTheme.colorScheme.background)) {
                    QueensReplayBoard.Board(frame, ReplayBoardOptions(animate = false, showErrors = showErrors), Modifier.size(320.dp))
                }
            }
        }
        composeRule.waitForIdle()
        val on = pixels()
        showErrors = false
        composeRule.waitForIdle()
        return on to pixels()
    }

    // Queens 1 and 2 share a row: the stripes are the mark.
    private val clash = listOf(QueensReplayMove.Place(Cell(1)), QueensReplayMove.Place(Cell(2)))

    @Test fun `errors on draw the conflict stripes that errors off hide`() {
        val (on, off) = shown(frameOf(clash))
        // The stripes are extra colours on the clashing cells: errors on paint more of them than errors off.
        assertTrue("errors on: ${on.toSet().size} colours, off: ${off.toSet().size}", on.toSet().size > off.toSet().size)
    }
}
