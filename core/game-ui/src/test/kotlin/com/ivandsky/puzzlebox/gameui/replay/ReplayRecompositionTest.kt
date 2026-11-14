package com.ivandsky.puzzlebox.gameui.replay

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.replay.ReplayFrame
import com.ivandsky.puzzlebox.testing.replay.ReplayFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** CR-2: the playback position moves every frame, but a board recomposes only when its frame changes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ReplayRecompositionTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** One move a second: the first at 1 000. */
    private val mine = ReplayFixtures.timeline(6)
    private val theirs = ReplayFixtures.timeline(12)

    private var boardCompositions = 0

    private val board = object : GameReplayBoard {
        override val pieces = "pieces"

        @Composable
        override fun Board(frame: ReplayFrame, options: ReplayBoardOptions, modifier: Modifier) {
            SideEffect { boardCompositions++ }
            Text("facts ${frame.facts.correct}")
        }
    }

    private fun startPlayingFromZero() {
        composeRule.mainClock.autoAdvance = false
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag(ReplayTags.SEEK).performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag(ReplayTags.PLAY).performClick()
        composeRule.mainClock.advanceTimeByFrame()
        // Settle the options (a seek's one resting frame ends with the first tick) and pass the 500 ms settle window
        // of the frame before the first move, the one time a frame changes with no move; the first move is at 1 000.
        composeRule.mainClock.advanceTimeBy(600)
    }

    @Test
    fun `the single-board player recomposes its board on a move, not on every tick between moves`() {
        composeRule.setContent { PuzzleBoxTheme(darkTheme = false) { ReplayPlayer(mine, board) } }
        startPlayingFromZero()
        val before = boardCompositions

        // 600 ms to 900 ms: about 20 frames, no move stamp inside.
        composeRule.mainClock.advanceTimeBy(300)

        assertEquals("ticks inside one frame leave the board alone", before, boardCompositions)

        composeRule.mainClock.advanceTimeBy(300)

        assertTrue("the move at 1 000 recomposes it", boardCompositions > before)
    }

    @Test
    fun `the compare recomposes both boards on a move, not on every tick between moves`() {
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                ReplayCompare(mine, theirs, board, CompareNames("You", "Maya"), CompareLayout.Twin, Modifier.fillMaxSize())
            }
        }
        startPlayingFromZero()
        val before = boardCompositions

        composeRule.mainClock.advanceTimeBy(300)

        assertEquals(before, boardCompositions)
    }
}
