package com.ivandsky.puzzlebox.games.zipui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.game.Puzzle
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.DragEvent
import com.ivandsky.puzzlebox.gameui.GridCell
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
import java.time.LocalDate
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * #1184: Zip's refusal ("Go to 2 first") sits centred directly under the board, as the tutorial's
 * messages do, on a phone and on a portrait tablet. The caption node clears its children's
 * semantics, so the text's horizontal extent is read from its pixels.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ZipRefusalCaptionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun refusedController(): ZipPlayController {
        val open: Puzzle = ZipPlayFixture.puzzle().let { ZipPlayFixture.envelope(it.copy(board = it.board.copy(walls = emptySet()))) }
        val controller = ZipPlayController(PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), {}, {}))
        check(controller.adoptToday(open, ZipPlayFixture.ref(LocalDate.of(2026, 10, 31))) == Adopt.Adopted)
        listOf(0, 1, 2, 3, 4, 9, 14, 19).forEach { controller.onDragEvent(DragEvent.Extend(GridCell(it / 5, it % 5))) }
        controller.onDragEvent(DragEvent.Extend(GridCell(4, 4)))
        check(controller.rejection == "Go to 2 first")
        return controller
    }

    private fun assertCentredUnderBoard() {
        val controller = refusedController()
        composeRule.setContent {
            PuzzleBoxTheme {
                ZipPlayScreen(controller, PlayBoardStatus.Ready, PlayLayout.SinglePane, null, {}, { _, _ -> })
            }
        }
        composeRule.waitForIdle()

        val board = composeRule.onNodeWithTag("play.board").fetchSemanticsNode().boundsInRoot
        val slotNode = composeRule.onNodeWithTag("play.caption_slot")
        val slot = slotNode.fetchSemanticsNode().boundsInRoot
        val pixels = slotNode.captureToImage().toPixelMap()
        val background = pixels[0, 0]
        val inked = (0 until pixels.width).filter { x -> (0 until pixels.height).any { y -> pixels[x, y] != background } }
        check(inked.isNotEmpty()) { "caption drew nothing" }

        val textCentre = slot.left + (inked.first() + inked.last() + 1) / 2f
        check(slot.top >= board.bottom) { "caption $slot not below board $board" }
        check(abs(textCentre - board.center.x) <= 3f) { "caption centre $textCentre not on board centre ${board.center.x}" }
    }

    @Config(sdk = [34], qualifiers = "w411dp-h891dp")
    @Test
    fun `phone refusal is centred under the board`() = assertCentredUnderBoard()

    @Config(sdk = [34], qualifiers = "w800dp-h1280dp")
    @Test
    fun `portrait tablet refusal is centred under the board`() = assertCentredUnderBoard()
}
