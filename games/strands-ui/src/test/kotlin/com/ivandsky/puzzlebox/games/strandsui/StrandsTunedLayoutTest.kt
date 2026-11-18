package com.ivandsky.puzzlebox.games.strandsui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// nav-rethink B5: Strands' Hint sits under the board at the shared control row's Hint slot, the
// board holds its cap and its 48dp cell floor, and the landscape side column stacks the counter over Hint. Mdpi windows, so a dp is a pixel.
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-mdpi")
class StrandsTunedLayoutTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun adopted() = StrandsPlayController(
        PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), {}, {}),
    ).also { assertEquals(Adopt.Adopted, it.adoptToday(RealDay.fetched.puzzle, RealDay.fetched.ref)) }

    private fun show(layout: PlayLayout) {
        val controller = adopted()
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    StrandsPlayScreen(
                        controller = controller,
                        status = PlayBoardStatus.Ready,
                        layout = layout,
                        sidePanel = null,
                        onRetry = {},
                        onSolved = { _, _ -> },
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun rect(tag: String): Rect =
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    @Test
    fun `phone - the counter and Hint sit in one row under the board, inside its span`() {
        show(PlayLayout.SinglePane)

        val board = rect("strands.board")
        val counter = rect("strands.counter")
        val hint = rect("strands.hint")
        assertTrue("hint $hint under board $board", hint.top >= board.bottom - 1f)
        assertTrue("hint $hint inside board $board", hint.left >= board.left - 1f && hint.right <= board.right + 1f)
        assertTrue("counter $counter left of hint $hint", counter.right <= hint.left + 1f)
        assertTrue("hint ${hint.height} under the touch floor", hint.height >= 47.5f && hint.width >= 47.5f)
    }

    @Test
    fun `phone - the board holds the 48dp cell floor`() {
        show(PlayLayout.SinglePane)

        val board = rect("strands.board")
        assertTrue("cell ${board.width / 6}", board.width / 6 >= 47.5f)
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp-mdpi")
    fun `tablet portrait - the board holds the 6 column cell cap and Hint stays under it`() {
        show(PlayLayout.SinglePane)

        val board = rect("strands.board")
        val hint = rect("strands.hint")
        assertTrue("board ${board.width} over the 576dp cap (6 cells of 96dp)", board.width <= 576f + 1f)
        assertTrue("hint $hint under board $board", hint.top >= board.bottom - 1f)
        assertTrue("hint $hint inside board $board", hint.left >= board.left - 1f && hint.right <= board.right + 1f)
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-mdpi")
    fun `tablet landscape side column - the counter stacks over Hint beside the board`() {
        show(PlayLayout.TwoPane)

        val board = rect("strands.board")
        val counter = rect("strands.counter")
        val hint = rect("strands.hint")
        assertTrue("hint $hint right of board $board", hint.left >= board.right - 1f)
        assertTrue("counter $counter over hint $hint", counter.bottom <= hint.top + 1f)
    }
}
