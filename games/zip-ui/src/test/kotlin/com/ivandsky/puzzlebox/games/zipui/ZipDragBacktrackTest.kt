package com.ivandsky.puzzlebox.games.zipui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
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
 * Ticket 1572: on the play screen, dragging back along the drawn line shortens it, also round a
 * corner the finger cuts. One gesture on the 5x5 fixture: along row 0, down at its end, one cell
 * left, then back round the corner at cell 9. Fingers are in cells from the board's top-left.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w800dp-h1280dp-mdpi")
class ZipDragBacktrackTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var controller: ZipPlayController

    private fun show() {
        controller = ZipPlayController(PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), {}, {}))
        check(controller.adoptToday(ZipPlayFixture.envelope(), ZipPlayFixture.ref(LocalDate.of(2026, 10, 31))) == Adopt.Adopted)
        composeRule.setContent {
            PuzzleBoxTheme(darkTheme = false) {
                ZipPlayScreen(controller, PlayBoardStatus.Ready, PlayLayout.SinglePane, sidePanel = null, onRetry = {}, onSolved = { _, _ -> })
            }
        }
        composeRule.waitForIdle()
    }

    private fun drag(vararg points: Pair<Float, Float>) = composeRule.onNodeWithTag("play.board").performTouchInput {
        for ((x, y) in points) moveTo(Offset(width * x / CELLS, height * y / CELLS), delayMillis = 0)
    }

    @Test
    fun `dragging back two cells round a corner the finger cuts shortens the line by two`() {
        show()
        composeRule.onNodeWithTag("play.board").performTouchInput { down(Offset(width * 0.5f / CELLS, height * 0.5f / CELLS)) }
        drag(1f to 0.5f, 1.5f to 0.5f, 2f to 0.5f, 2.5f to 0.5f, 3f to 0.5f, 3.5f to 0.5f, 4f to 0.5f, 4.5f to 0.5f)
        drag(4.5f to 1f, 4.5f to 1.5f, 4f to 1.5f, 3.5f to 1.5f)
        composeRule.waitForIdle()
        assertEquals(ZipPlayFixture.path(0, 1, 2, 3, 4, 9, 8), controller.session?.path)

        // Back right toward cell 9 and up into cell 4, rounding cell 9 wide of its middle.
        drag(3.8f to 1.5f, 4.2f to 1.1f, 4.45f to 0.8f, 4.5f to 0.55f)
        composeRule.onNodeWithTag("play.board").performTouchInput { up() }
        composeRule.waitForIdle()
        assertEquals(ZipPlayFixture.path(0, 1, 2, 3, 4), controller.session?.path)
    }

    private companion object {
        const val CELLS = 5f
    }
}
