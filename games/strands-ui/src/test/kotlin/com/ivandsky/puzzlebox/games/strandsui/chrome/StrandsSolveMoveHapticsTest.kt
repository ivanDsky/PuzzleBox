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
import com.ivandsky.puzzlebox.gameui.LocalPlayFeel
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
 * With PlayFeel on, the board's move haptics replace the legacy per-verdict buzzes: a drag that
 * solves the board buzzes exactly one Confirm (the solve confirm from the rest), and a rejected
 * drag buzzes once.
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
                LocalPlayFeel provides true,
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

    /** Drags along [path] on the board, as a finger does. */
    private fun drag(path: List<Coord>) {
        rule.onNodeWithTag("strands.board").performTouchInput {
            down(centreOf(path.first()))
            path.drop(1).forEach { moveTo(centreOf(it)) }
            up()
        }
        rule.waitForIdle()
    }

    // Frame by frame: with nothing animating, one big step would skip past the timer's wake-up.
    private fun advance(ms: Long) = repeat((ms / STEP_MS).toInt()) { rule.mainClock.advanceTimeBy(STEP_MS) }

    @Test
    fun `with PlayFeel on a drag that solves the board buzzes one confirm`() {
        val controller = adopted()
        show(controller)
        (0 until 3).forEach { row -> drag(StrandsUiFixtures.row(row)) }
        buzzes.clear()

        drag(StrandsUiFixtures.row(3))
        advance(CelebrationMotion.SolveMomentMs * 2)

        assertEquals(listOf(HapticFeedbackType.Confirm), buzzes.filterNot { it == HapticFeedbackType.TextHandleMove })
    }

    @Test
    fun `with PlayFeel on a rejected word buzzes once`() {
        val controller = adopted()
        show(controller)

        drag(listOf(Coord(0, 0), Coord(0, 1), Coord(1, 2)))

        assertEquals(1, buzzes.count { it == HapticFeedbackType.Reject })
        assertEquals(listOf(HapticFeedbackType.Reject), buzzes.filterNot { it == HapticFeedbackType.TextHandleMove })
    }

    private companion object {
        const val STEP_MS = 16L
    }
}
