package com.ivandsky.puzzlebox.games.strandsui.chrome

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.ivandsky.puzzlebox.designsystem.CelebrationMotion
import com.ivandsky.puzzlebox.designsystem.PuzzleBoxTheme
import com.ivandsky.puzzlebox.games.strands.Coord
import com.ivandsky.puzzlebox.games.strandsui.StrandsPlayController
import com.ivandsky.puzzlebox.games.strandsui.StrandsPlayScreen
import com.ivandsky.puzzlebox.games.strandsui.StrandsUiFixtures
import com.ivandsky.puzzlebox.gameui.Adopt
import com.ivandsky.puzzlebox.gameui.LocalSolveRestOnBoard
import com.ivandsky.puzzlebox.gameui.PlayBoardStatus
import com.ivandsky.puzzlebox.gameui.PlayEnvironment
import com.ivandsky.puzzlebox.gameui.PlayLayout
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
 * The board's move haptics: a word ticks
 * per letter joined and once when found, a refused word buzzes once, and the solving lift buzzes
 * only the solve confirm from the rest.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class StrandsSolveMoveHapticsTest {

    @get:Rule
    val rule = createComposeRule()

    private val buzzes = ArrayList<HapticFeedbackType>()

    private val haptics = object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            buzzes += hapticFeedbackType
        }
    }

    private fun adopted() = StrandsPlayController(
        PlayEnvironment(CoroutineScope(Dispatchers.Unconfined), {}, {}),
    ).also { assertEquals(Adopt.Adopted, it.adoptToday(StrandsUiFixtures.envelope(), StrandsUiFixtures.ref)) }

    private fun show(controller: StrandsPlayController) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(
                LocalSolveRestOnBoard provides true,
                LocalHapticFeedback provides haptics,
            ) {
                PuzzleBoxTheme(darkTheme = false) {
                    StrandsPlayScreen(
                        controller = controller,
                        status = PlayBoardStatus.Ready,
                        layout = PlayLayout.SinglePane,
                        sidePanel = null,
                        onRetry = {},
                        onSolved = { _, _ -> },
                    )
                }
            }
        }
        rule.mainClock.advanceTimeBy(16)
    }

    private fun TouchInjectionScope.centreOf(at: Coord) =
        Offset(width * (at.col + 0.5f) / 4, height * (at.row + 0.5f) / 4)

    /** Drags along [path] and lifts, as a finger does. */
    private fun drag(path: List<Coord>) {
        press(path)
        lift()
    }

    /** Puts the finger down on [path] and drags it along; the finger stays down. */
    private fun press(path: List<Coord>) {
        rule.onNodeWithTag("strands.board").performTouchInput { down(centreOf(path.first())) }
        rule.waitForIdle()
        // One frame per letter: a single gesture would recompose once, and the effect would see one step.
        path.drop(1).forEach { at ->
            rule.onNodeWithTag("strands.board").performTouchInput { moveTo(centreOf(at)) }
            rule.waitForIdle()
        }
    }

    private fun lift() {
        rule.onNodeWithTag("strands.board").performTouchInput { up() }
        rule.waitForIdle()
    }

    // Frame by frame: with nothing animating, one big step would skip past the timer's wake-up.
    private fun advance(ms: Long) = repeat((ms / STEP_MS).toInt()) { rule.mainClock.advanceTimeBy(STEP_MS) }

    @Test
    fun `a drag that solves the board buzzes one confirm, on the lift`() {
        val controller = adopted()
        show(controller)
        (0 until 3).forEach { row -> drag(StrandsUiFixtures.row(row)) }

        press(StrandsUiFixtures.row(3))
        buzzes.clear()
        lift()
        advance(CelebrationMotion.SolveMomentMs * 2)

        assertEquals(listOf(HapticFeedbackType.Confirm), buzzes)
    }

    @Test
    fun `a word found buzzes a tick per join and one for the word`() {
        val controller = adopted()
        show(controller)

        drag(StrandsUiFixtures.row(1))

        // Four letters: three joins tick, and the found word ticks once on the lift.
        assertEquals(List(4) { HapticFeedbackType.TextHandleMove }, buzzes)
    }

    @Test
    fun `a rejected word buzzes a tick per join and one reject`() {
        val controller = adopted()
        show(controller)

        drag(listOf(Coord(0, 0), Coord(0, 1), Coord(1, 2)))

        assertEquals(List(2) { HapticFeedbackType.TextHandleMove } + HapticFeedbackType.Reject, buzzes)
    }

    private companion object {
        const val STEP_MS = 16L
    }
}
